package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.licenciamento.ApiComercial;
import br.com.instadino.licenciamento.ArmazemDeConta;
import br.com.instadino.licenciamento.ChavesConfiaveis;
import br.com.instadino.licenciamento.ClienteHttp;
import br.com.instadino.licenciamento.GerenciadorDeLicenca;
import br.com.instadino.licenciamento.VerificadorDeLicenca;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorSomenteDesenvolvimento;
import br.com.instadino.servidor.dominio.Concessao;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeConcessoes;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeContas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePlanos;
import br.com.instadino.servidor.email.MensagemDeEmail.Tipo;
import br.com.instadino.servidor.servico.AssinadorDeLicencas;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Cliente REAL do desktop (HTTP) contra o servidor REAL em uma porta aleatória, com a licença validada pela chave
 * pública do servidor. Nada é simulado entre as duas pontas além do relógio e do envio de e-mail.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("teste")
@Import(ConfiguracaoDeTeste.class)
@TestPropertySource(properties = {"PAYMENTS_ENABLED=true", "PAYMENTS_MODE=sandbox", "PAYMENTS_WEBHOOK_SECRET=segredo-de-webhook-de-teste-32-bytes!"})
class IntegracaoClienteServidorTest {

    static final String SENHA = "Uma-senha-bem-longa-42!";

    @LocalServerPort
    int porta;
    @Autowired
    RelogioMutavel relogio;
    @Autowired
    EmailEmMemoria emails;
    @Autowired
    AssinadorDeLicencas assinador;
    @Autowired
    RepositorioDeContas contas;
    @Autowired
    RepositorioDeConcessoes concessoes;
    @Autowired
    RepositorioDePlanos planos;
    @Autowired
    PasswordEncoder codificador;
    @TempDir
    Path pasta;

    private ClienteHttp cliente() {
        return new ClienteHttp(URI.create("http://127.0.0.1:" + porta), true);
    }

    private GerenciadorDeLicenca gerenciador(ApiComercial api, String dispositivo) {
        return new GerenciadorDeLicenca(api, new ArmazemDeConta(pasta.resolve("conta-" + dispositivo + ".cache"), new ProtetorSomenteDesenvolvimento()),
                new VerificadorDeLicenca(ChavesConfiaveis.de("k1", assinador.chavePublica())), relogio, dispositivo, "PC de integração");
    }

    private String criar(Papel papel) {
        String email = "int-" + UUID.randomUUID().toString().substring(0, 8) + "@instadino.test";
        contas.save(new br.com.instadino.servidor.dominio.Conta(email, email, "Teste", codificador.encode(SENHA), papel, true, relogio.instant()));
        return email;
    }

    private void garantirPlano() {
        if (planos.findById("plano-integracao").isEmpty()) {
            planos.save(new br.com.instadino.servidor.dominio.Plano("plano-integracao", "Premium (TESTE)", "Plano de teste", "MENSAL", 1990, "BRL", true));
        }
    }

    @Test
    void clienteFreeEntraENaoRecebeLicenca() {
        var g = gerenciador(cliente(), "disp-free-0000001");
        var r = g.entrar(criar(Papel.CLIENTE), SENHA);
        assertThat(r.sucesso()).isTrue();
        assertThat(g.autenticado()).isTrue();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
    }

    @Test
    void masterRecebeLicencaPremiumValidadaPelaChavePublicaDoServidor() {
        var g = gerenciador(cliente(), "disp-master-00001");
        var r = g.entrar(criar(Papel.MASTER), SENHA);
        assertThat(r.sucesso()).isTrue();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.PREMIUM);
        assertThat(g.estadoAtual().situacao()).isEqualTo(SituacaoDaLicenca.ATIVA);
    }

    @Test
    void contaComConcessaoPremiumRecebeLicenca() {
        String email = criar(Papel.CLIENTE);
        var conta = contas.findByEmailNormalizado(email).orElseThrow();
        concessoes.save(new Concessao(conta.getId(), "CONCESSAO_ADMINISTRATIVA", relogio.instant(), null, null, "teste de integração"));
        var g = gerenciador(cliente(), "disp-premium-0001");
        assertThat(g.entrar(email, SENHA).sucesso()).isTrue();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.PREMIUM);
    }

    @Test
    void compraEmSandboxLiberaOPremiumEReembolsoDevolveAoFree() {
        garantirPlano();
        var api = cliente();
        var g = gerenciador(api, "disp-compra-000001");
        g.entrar(criar(Papel.CLIENTE), SENHA);
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);

        var catalogo = api.catalogo();
        assertThat(catalogo.comprasHabilitadas()).isTrue();
        var checkout = api.criarCheckout(g.token().orElseThrow(), "plano-integracao");
        assertThat(checkout.sandbox()).isTrue();

        api.simularEventoDeSandbox(g.token().orElseThrow(), checkout.pedidoId(), "PAGAMENTO_APROVADO");
        assertThat(g.atualizarLicenca().sucesso()).isTrue();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.PREMIUM);

        relogio.avancar(Duration.ofSeconds(2));
        api.simularEventoDeSandbox(g.token().orElseThrow(), checkout.pedidoId(), "PAGAMENTO_REEMBOLSADO");
        var r = g.atualizarLicenca();
        assertThat(r.sucesso()).isTrue();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        assertThat(api.historico(g.token().orElseThrow())).hasSize(1);
    }

    @Test
    void cadastroVerificacaoELoginPelaRedeFuncionam() {
        var api = cliente();
        String email = "novo-" + UUID.randomUUID().toString().substring(0, 8) + "@instadino.test";
        api.cadastrar(email, SENHA, "Maria Nova");
        var g = gerenciador(api, "disp-novo-0000001");
        assertThat(g.entrar(email, SENHA).sucesso()).as("e-mail ainda não verificado").isFalse();
        api.verificarEmail(emails.ultima(email, Tipo.VERIFICACAO_EMAIL).token());
        assertThat(g.entrar(email, SENHA).sucesso()).isTrue();
        assertThat(g.conta()).isPresent();
    }

    @Test
    void sairRevogaASessaoNoServidor() {
        var api = cliente();
        var g = gerenciador(api, "disp-sair-00000001");
        g.entrar(criar(Papel.CLIENTE), SENHA);
        String token = g.token().orElseThrow();
        assertThat(g.sair().sucesso()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> api.conta(token)).isInstanceOf(br.com.instadino.licenciamento.ErroDaApi.class);
    }

    // ---- fraudes na rede ----

    /** Intercepta a resposta da licença, como faria um proxy malicioso, e a modifica. */
    private ApiComercial comLicencaAdulterada(ApiComercial real, UnaryOperator<LicencaAssinada> adulteracao) {
        InvocationHandler h = (proxy, metodo, args) -> {
            try {
                Object resultado = metodo.invoke(real, args);
                return metodo.getName().equals("ativarLicenca") ? adulteracao.apply((LicencaAssinada) resultado) : resultado;
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (ApiComercial) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ApiComercial.class}, h);
    }

    @Test
    void licencaAdulteradaNaRedeENaoEAceita() {
        String email = criar(Papel.CLIENTE);
        concessoes.save(new Concessao(contas.findByEmailNormalizado(email).orElseThrow().getId(), "CONCESSAO_ADMINISTRATIVA", relogio.instant(),
                relogio.instant().plus(Duration.ofDays(1)), null, "teste"));
        UnaryOperator<LicencaAssinada> estender = l -> {
            String texto = new String(Base64.getUrlDecoder().decode(l.payload()));
            String novo = texto.replaceAll("\"validaAte\":\"[^\"]+\"", "\"validaAte\":\"2099-01-01T00:00:00Z\"")
                    .replaceAll("\"toleranciaOfflineAte\":\"[^\"]+\"", "\"toleranciaOfflineAte\":\"2099-01-02T00:00:00Z\"");
            return new LicencaAssinada(Base64.getUrlEncoder().withoutPadding().encodeToString(novo.getBytes()), l.assinatura(), l.kid(), l.algoritmo());
        };
        var g = gerenciador(comLicencaAdulterada(cliente(), estender), "disp-fraude-0000001");
        var r = g.entrar(email, SENHA);
        assertThat(r.sucesso()).isFalse();
        assertThat(r.mensagem()).contains("ASSINATURA_INVALIDA");
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
    }

    @Test
    void servidorFalsoComOutraChaveNaoConcedePremium() throws Exception {
        // o cliente só confia na chave do servidor verdadeiro; uma licença assinada por outra chave é recusada
        var par = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var g = new GerenciadorDeLicenca(cliente(), new ArmazemDeConta(pasta.resolve("falso.cache"), new ProtetorSomenteDesenvolvimento()),
                new VerificadorDeLicenca(ChavesConfiaveis.de("k1", par.getPublic())), relogio, "disp-falso-00000001", "PC");
        var r = g.entrar(criar(Papel.MASTER), SENHA);
        assertThat(r.sucesso()).isFalse();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
    }

    @Test
    void semDireitoEstadoLocalPermaneceFreeMesmoComTokenValido() {
        var g = gerenciador(cliente(), "disp-semdireito-01");
        g.entrar(criar(Papel.CLIENTE), SENHA);
        var estado = g.estadoAtual();
        assertThat(estado).isEqualTo(new EstadoDaLicenca(Plano.FREE, SituacaoDaLicenca.AUSENTE));
    }
}
