package br.com.instadino.desktop.telas;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.contratos.conta.RespostaDeLogin;
import br.com.instadino.contratos.conta.ResumoDaConta;
import br.com.instadino.contratos.conta.SessaoAtiva;
import br.com.instadino.contratos.licenca.DispositivoAutorizado;
import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.contratos.licenca.PayloadDeLicenca;
import br.com.instadino.contratos.pagamento.EstadoDaAssinatura;
import br.com.instadino.contratos.pagamento.ItemDoHistorico;
import br.com.instadino.contratos.pagamento.PlanoComercial;
import br.com.instadino.contratos.pagamento.RespostaDeCatalogo;
import br.com.instadino.contratos.pagamento.RespostaDeCheckout;
import br.com.instadino.contratos.pagamento.SituacaoDosPagamentos;
import br.com.instadino.licenciamento.ApiComercial;
import br.com.instadino.licenciamento.ErroDaApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Serviço comercial falso para os testes de interface. Assina licenças de verdade (Ed25519) com uma chave de teste. */
final class ApiDeTeste implements ApiComercial {

    static final String SENHA = "senha-correta-123";

    final KeyPair chaves;
    final UUID contaId = UUID.randomUUID();
    final String email = "maria@instadino.test";
    volatile boolean premium;
    volatile boolean comprasHabilitadas = true;
    volatile boolean sandbox = true;
    final List<String> chamadas = new ArrayList<>();
    final List<ItemDoHistorico> pedidos = new ArrayList<>();
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    ApiDeTeste() {
        try {
            chaves = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public RespostaDeLogin entrar(String email, String senha, String idDoDispositivo, String nome) {
        chamadas.add("entrar");
        if (!email.equals(this.email) || !senha.equals(SENHA)) {
            throw new ErroDaApi(401, "CREDENCIAIS_INVALIDAS", "E-mail ou senha incorretos, ou conta temporariamente indisponível.");
        }
        return new RespostaDeLogin("token-secreto-de-sessao-ABCDEFGHIJKL", Instant.now().plus(Duration.ofDays(14)),
                new ResumoDaConta(contaId, this.email, "Maria Teste", Papel.CLIENTE, true));
    }

    @Override
    public LicencaAssinada ativarLicenca(String token, String idDoDispositivo, String nome) {
        chamadas.add("ativarLicenca");
        if (!premium) {
            throw new ErroDaApi(403, "LICENCA_INDISPONIVEL", "Esta conta não possui uma assinatura Premium ativa.");
        }
        try {
            Instant agora = Instant.now();
            var p = new PayloadDeLicenca(1, UUID.randomUUID(), PayloadDeLicenca.PRODUTO, "PREMIUM", contaId, idDoDispositivo, agora,
                    agora.plus(Duration.ofDays(7)), agora.plus(Duration.ofDays(14)), "ASSINATURA");
            byte[] bytes = json.writeValueAsBytes(p);
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(chaves.getPrivate());
            s.update(bytes);
            Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
            return new LicencaAssinada(b64.encodeToString(bytes), b64.encodeToString(s.sign()), "k1", "Ed25519");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public RespostaDeCatalogo catalogo() {
        chamadas.add("catalogo");
        var plano = new PlanoComercial("p1", "Premium mensal (TESTE)", "Plano de teste", "MENSAL", 1990, "BRL");
        if (!comprasHabilitadas) {
            return new RespostaDeCatalogo(List.of(plano), SituacaoDosPagamentos.DESABILITADO, false,
                    "A compra do plano Premium ainda não está habilitada. Nenhuma cobrança é feita por este aplicativo.");
        }
        return new RespostaDeCatalogo(List.of(plano), sandbox ? SituacaoDosPagamentos.SANDBOX_ATIVO : SituacaoDosPagamentos.PRODUCAO_ATIVA, true,
                sandbox ? "Modo de teste: nenhuma cobrança real será feita." : null);
    }

    @Override
    public RespostaDeCheckout criarCheckout(String token, String planoId) {
        chamadas.add("checkout");
        UUID pedido = UUID.randomUUID();
        pedidos.add(new ItemDoHistorico(pedido, planoId, 1990, "BRL", "PENDENTE", Instant.now()));
        return new RespostaDeCheckout(pedido, "https://checkout.sandbox.instadino.invalid/pedido/" + pedido, sandbox);
    }

    @Override
    public void simularEventoDeSandbox(String token, UUID pedidoId, String tipo) {
        chamadas.add("simular:" + tipo);
        if (tipo.equals("PAGAMENTO_APROVADO")) {
            premium = true;
        } else if (tipo.equals("PAGAMENTO_REEMBOLSADO")) {
            premium = false;
        }
    }

    @Override
    public List<ItemDoHistorico> historico(String token) {
        return List.copyOf(pedidos);
    }

    @Override
    public EstadoDaAssinatura assinatura(String token) {
        return new EstadoDaAssinatura(premium ? "ATIVA" : "SEM_ASSINATURA", premium ? "p1" : null, premium ? Instant.now().plus(Duration.ofDays(30)) : null,
                premium, premium ? "ASSINATURA" : null);
    }

    @Override
    public List<DispositivoAutorizado> dispositivos(String token) {
        return List.of(new DispositivoAutorizado(UUID.randomUUID(), "Computador com Linux (disp)", Instant.now()));
    }

    @Override
    public List<SessaoAtiva> sessoes(String token) {
        return List.of(new SessaoAtiva(UUID.randomUUID(), "Computador com Linux (disp)", Instant.now(), Instant.now(), Instant.now().plus(Duration.ofDays(14)), true));
    }

    @Override
    public void sair(String token) {
        chamadas.add("sair");
    }

    @Override
    public ResumoDaConta conta(String token) {
        return new ResumoDaConta(contaId, email, "Maria Teste", Papel.CLIENTE, true);
    }

    @Override
    public void cadastrar(String email, String senha, String nome) {
        chamadas.add("cadastrar");
    }

    @Override
    public void verificarEmail(String token) {
        chamadas.add("verificar");
    }

    @Override
    public void revogarSessao(String token, UUID sessaoId) {
    }

    @Override
    public void solicitarRecuperacao(String email) {
        chamadas.add("recuperar");
    }

    @Override
    public void redefinirSenha(String t, String s) {
        chamadas.add("redefinir");
    }

    @Override
    public void removerDispositivo(String t, UUID d) {
    }
}
