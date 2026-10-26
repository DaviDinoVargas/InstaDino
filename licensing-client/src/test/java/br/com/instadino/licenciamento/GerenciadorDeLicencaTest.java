package br.com.instadino.licenciamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.contratos.conta.RespostaDeLogin;
import br.com.instadino.contratos.conta.ResumoDaConta;
import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.contratos.pagamento.EstadoDaAssinatura;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorDeSegredo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GerenciadorDeLicencaTest {

    /** API falsa: emite licenças como o servidor, sob controle do teste. */
    static final class ApiFalsa implements ApiComercial {
        final LicencasDeTeste emissor;
        final RelogioDeTeste relogio;
        final ResumoDaConta conta;
        boolean temDireito = true;
        boolean foraDoAr;
        boolean sessaoInvalida;
        int diasDeValidade = 7;
        Function<String, UUID> titular = d -> null;
        int logouts;

        ApiFalsa(LicencasDeTeste emissor, RelogioDeTeste relogio, ResumoDaConta conta) {
            this.emissor = emissor;
            this.relogio = relogio;
            this.conta = conta;
        }

        @Override
        public RespostaDeLogin entrar(String email, String senha, String idDoDispositivo, String nome) {
            if (foraDoAr) {
                throw new FalhaDeRede();
            }
            if (!senha.equals("senha-correta-123")) {
                throw new ErroDaApi(401, "CREDENCIAIS_INVALIDAS", "E-mail ou senha incorretos.");
            }
            return new RespostaDeLogin("token-de-sessao-secreto-0123456789", relogio.instant().plus(Duration.ofDays(14)), conta);
        }

        @Override
        public LicencaAssinada ativarLicenca(String token, String idDoDispositivo, String nome) {
            if (foraDoAr) {
                throw new FalhaDeRede();
            }
            if (sessaoInvalida) {
                throw new ErroDaApi(401, "NAO_AUTENTICADO", "Entre na sua conta.");
            }
            if (!temDireito) {
                throw new ErroDaApi(403, "LICENCA_INDISPONIVEL", "Sem assinatura.");
            }
            UUID dono = titular.apply(idDoDispositivo) != null ? titular.apply(idDoDispositivo) : conta.id();
            return emissor.assinar(emissor.payload(dono, idDoDispositivo, relogio.instant(), diasDeValidade, 7));
        }

        @Override
        public void sair(String token) {
            logouts++;
        }

        @Override public void cadastrar(String e, String s, String n) { }
        @Override public void verificarEmail(String t) { }
        @Override public ResumoDaConta conta(String t) { return conta; }
        @Override public List<br.com.instadino.contratos.conta.SessaoAtiva> sessoes(String t) { return List.of(); }
        @Override public void revogarSessao(String t, UUID s) { }
        @Override public void solicitarRecuperacao(String e) { }
        @Override public void redefinirSenha(String t, String s) { }
        @Override public EstadoDaAssinatura assinatura(String t) { return null; }
        @Override public List<br.com.instadino.contratos.licenca.DispositivoAutorizado> dispositivos(String t) { return List.of(); }
        @Override public void removerDispositivo(String t, UUID d) { }
        @Override public br.com.instadino.contratos.pagamento.RespostaDeCatalogo catalogo() { return null; }
        @Override public br.com.instadino.contratos.pagamento.RespostaDeCheckout criarCheckout(String t, String p) { return null; }
        @Override public List<br.com.instadino.contratos.pagamento.ItemDoHistorico> historico(String t) { return List.of(); }
        @Override public void simularEventoDeSandbox(String t, UUID p, String tipo) { }
    }

    /** Protetor que realmente transforma os bytes, para provar que o cache não guarda o token em claro. */
    static final class Mascara implements ProtetorDeSegredo {
        @Override
        public byte[] proteger(byte[] s) {
            byte[] r = new byte[s.length + 2];
            r[0] = 'P';
            r[1] = 'X';
            for (int i = 0; i < s.length; i++) {
                r[i + 2] = (byte) (s[i] ^ 0x5A);
            }
            return r;
        }

        @Override
        public byte[] revelar(byte[] p) {
            if (p.length < 2 || p[0] != 'P' || p[1] != 'X') {
                throw new IllegalStateException("conteúdo inválido");
            }
            byte[] r = new byte[p.length - 2];
            for (int i = 0; i < r.length; i++) {
                r[i] = (byte) (p[i + 2] ^ 0x5A);
            }
            return r;
        }

        @Override public boolean seguroParaProducao() { return false; }
        @Override public String nome() { return "máscara de teste"; }
    }

    @TempDir
    Path pasta;

    final LicencasDeTeste emissor = new LicencasDeTeste("k1");
    final RelogioDeTeste relogio = new RelogioDeTeste();
    final ResumoDaConta conta = new ResumoDaConta(UUID.randomUUID(), "maria@instadino.test", "Maria", Papel.CLIENTE, true);
    ApiFalsa api;
    ArmazemDeConta armazem;
    String dispositivo = "dispositivo-de-teste-0001";

    @BeforeEach
    void preparar() {
        api = new ApiFalsa(emissor, relogio, conta);
        armazem = new ArmazemDeConta(pasta.resolve("conta.cache"), new Mascara());
    }

    private GerenciadorDeLicenca gerenciador() {
        return new GerenciadorDeLicenca(api, armazem, new VerificadorDeLicenca(emissor.confiaveis()), relogio, dispositivo, "PC de teste");
    }

    @Test
    void semServicoConfiguradoOPlanoEFree() {
        var g = new GerenciadorDeLicenca(null, armazem, new VerificadorDeLicenca(emissor.confiaveis()), relogio, dispositivo, "PC");
        assertThat(g.servicoConfigurado()).isFalse();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
    }

    @Test
    void loginComDireitoAtivaOPremiumNesteDispositivo() {
        var g = gerenciador();
        var r = g.entrar(conta.email(), "senha-correta-123");
        assertThat(r.sucesso()).isTrue();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.PREMIUM);
        assertThat(g.descricaoDaLicenca()).contains("ativa");
        assertThat(g.autenticado()).isTrue();
    }

    @Test
    void loginComSenhaErradaNaoConcedeNada() {
        var g = gerenciador();
        var r = g.entrar(conta.email(), "errada");
        assertThat(r.sucesso()).isFalse();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        assertThat(g.conta()).isEmpty();
    }

    @Test
    void contaSemDireitoUsaOPlanoFreeSemErro() {
        api.temDireito = false;
        var g = gerenciador();
        var r = g.entrar(conta.email(), "senha-correta-123");
        assertThat(r.sucesso()).isTrue();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        assertThat(g.autenticado()).isTrue();
    }

    @Test
    void aLicencaSobreviveAReinicioDoAplicativo() {
        gerenciador().entrar(conta.email(), "senha-correta-123");
        var reaberto = gerenciador();
        assertThat(reaberto.estadoAtual().planoEfetivo()).isEqualTo(Plano.PREMIUM);
        assertThat(reaberto.conta()).isPresent();
    }

    @Test
    void semInternetALicencaValeAteOFimDaToleranciaEDepoisVoltaAFree() {
        var g = gerenciador();
        g.entrar(conta.email(), "senha-correta-123");
        api.foraDoAr = true;
        relogio.avancar(Duration.ofDays(6));
        assertThat(g.estadoAtual().situacao()).isEqualTo(SituacaoDaLicenca.ATIVA);
        relogio.avancar(Duration.ofDays(2)); // dia 8: passou a validade, dentro da tolerância
        assertThat(g.estadoAtual().situacao()).isEqualTo(SituacaoDaLicenca.TOLERANCIA_OFFLINE);
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.PREMIUM);
        var falha = g.atualizarLicenca();
        assertThat(falha.sucesso()).isFalse();
        assertThat(falha.mensagem()).contains("tolerância");
        relogio.avancar(Duration.ofDays(7)); // dia 15: acabou a tolerância
        assertThat(g.estadoAtual().situacao()).isEqualTo(SituacaoDaLicenca.EXPIRADA);
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        assertThat(g.descricaoDaLicenca()).contains("dados locais continuam acessíveis");
    }

    @Test
    void renovarComInternetEstendeAValidade() {
        var g = gerenciador();
        g.entrar(conta.email(), "senha-correta-123");
        relogio.avancar(Duration.ofDays(6));
        assertThat(g.atualizarLicenca().sucesso()).isTrue();
        relogio.avancar(Duration.ofDays(6));
        assertThat(g.estadoAtual().situacao()).isEqualTo(SituacaoDaLicenca.ATIVA);
    }

    @Test
    void servidorNegandoOPremiumRebaixaImediatamenteParaFree() {
        var g = gerenciador();
        g.entrar(conta.email(), "senha-correta-123");
        api.temDireito = false; // reembolso, chargeback, concessão revogada...
        var r = g.atualizarLicenca();
        assertThat(r.sucesso()).isTrue();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        assertThat(gerenciador().estadoAtual().planoEfetivo()).as("persistido").isEqualTo(Plano.FREE);
    }

    @Test
    void sessaoExpiradaNoServidorLimpaOCache() {
        var g = gerenciador();
        g.entrar(conta.email(), "senha-correta-123");
        api.sessaoInvalida = true;
        var r = g.atualizarLicenca();
        assertThat(r.sucesso()).isFalse();
        assertThat(g.autenticado()).isFalse();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        assertThat(Files.exists(pasta.resolve("conta.cache"))).isFalse();
    }

    @Test
    void sairRemoveSessaoELicencaMasNaoApagaDadosLocais() throws Exception {
        Path dadosLocais = Files.writeString(pasta.resolve("instadino.db"), "dados do usuário");
        var g = gerenciador();
        g.entrar(conta.email(), "senha-correta-123");
        var r = g.sair();
        assertThat(r.sucesso()).isTrue();
        assertThat(api.logouts).isEqualTo(1);
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        assertThat(Files.exists(pasta.resolve("conta.cache"))).isFalse();
        assertThat(Files.readString(dadosLocais)).isEqualTo("dados do usuário");
    }

    // ---- fraudes ----

    @Test
    void licencaCopiadaDeOutroDispositivoNaoFunciona() {
        gerenciador().entrar(conta.email(), "senha-correta-123");
        dispositivo = "outro-computador-9999"; // o arquivo de cache foi copiado para outra máquina
        var g = gerenciador();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        assertThat(g.estadoAtual().situacao()).isEqualTo(SituacaoDaLicenca.INVALIDA);
    }

    @Test
    void licencaDeOutraContaNaoFunciona() {
        api.titular = d -> UUID.randomUUID();
        var g = gerenciador();
        var r = g.entrar(conta.email(), "senha-correta-123");
        assertThat(r.sucesso()).isFalse();
        assertThat(r.mensagem()).contains("TITULAR_DIFERENTE");
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
    }

    @Test
    void cacheComConteudoAdulteradoOuFalsificadoEIgnorado() throws Exception {
        gerenciador().entrar(conta.email(), "senha-correta-123");
        Path arquivo = pasta.resolve("conta.cache");
        byte[] original = Files.readAllBytes(arquivo);

        byte[] corrompido = original.clone();
        corrompido[corrompido.length / 2] ^= 0x7F;
        Files.write(arquivo, corrompido);
        assertThat(gerenciador().estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);

        // o usuário escreve um JSON de "conta Premium" à mão, sem a proteção do sistema
        Files.writeString(arquivo, "{\"token\":\"x\",\"conta\":{\"papel\":\"MASTER\"},\"licenca\":{\"payload\":\"e30\",\"assinatura\":\"AA\",\"kid\":\"k1\",\"algoritmo\":\"Ed25519\"}}");
        assertThat(gerenciador().estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        assertThat(gerenciador().conta()).isEmpty();
    }

    @Test
    void cacheNaoGuardaOTokenEmClaro() throws Exception {
        gerenciador().entrar(conta.email(), "senha-correta-123");
        String conteudo = new String(Files.readAllBytes(pasta.resolve("conta.cache")), StandardCharsets.ISO_8859_1);
        assertThat(conteudo).doesNotContain("token-de-sessao-secreto").doesNotContain("maria@instadino.test");
    }

    @Test
    void licencaForjadaNoCacheComChaveDoAtacanteNaoConcedePremium() throws Exception {
        var g = gerenciador();
        g.entrar(conta.email(), "senha-correta-123");
        // o atacante consegue gravar um cache válido para o protetor, com uma licença assinada por OUTRA chave
        var atacante = new LicencasDeTeste("k1");
        var forjada = atacante.assinar(atacante.payload(conta.id(), dispositivo, relogio.instant(), 3650, 3650));
        var dados = armazem.ler().orElseThrow().comLicenca(forjada, relogio.instant());
        armazem.gravar(dados);
        assertThat(gerenciador().estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
    }

    @Test
    void retrocederORelogioNaoEstendeALicenca() {
        var g = gerenciador();
        g.entrar(conta.email(), "senha-correta-123");
        relogio.avancar(Duration.ofDays(20)); // expirada
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        relogio.avancar(Duration.ofDays(-19)); // o usuário volta o relógio para dentro da validade
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.FREE);
        assertThat(g.estadoAtual().situacao()).isEqualTo(SituacaoDaLicenca.INVALIDA);
    }

    @Test
    void renovacaoOnlineAposRetrocessoDoRelogioRestauraOPremium() {
        var g = gerenciador();
        g.entrar(conta.email(), "senha-correta-123");
        relogio.avancar(Duration.ofDays(20));
        g.estadoAtual();
        relogio.avancar(Duration.ofDays(-19));
        assertThat(g.estadoAtual().situacao()).isEqualTo(SituacaoDaLicenca.INVALIDA);
        assertThat(g.atualizarLicenca().sucesso()).isTrue();
        assertThat(g.estadoAtual().planoEfetivo()).isEqualTo(Plano.PREMIUM);
    }

    @Test
    void toStringDosDadosNaoExpoeOToken() {
        var dados = new DadosDaConta("token-super-secreto", Instant.EPOCH, conta, null, null);
        assertThat(dados.toString()).doesNotContain("token-super-secreto");
    }
}
