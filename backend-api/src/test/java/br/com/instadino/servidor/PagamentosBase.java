package br.com.instadino.servidor;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.contratos.pagamento.VerificadorDeAssinaturaHmac;
import br.com.instadino.servidor.dominio.Plano;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeAssinaturas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeEventosDePagamento;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePedidos;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePlanos;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties = {"PAYMENTS_ENABLED=true", "PAYMENTS_MODE=sandbox", "PAYMENTS_WEBHOOK_SECRET=segredo-de-webhook-de-teste-32-bytes!"})
abstract class PagamentosBase extends TesteBase {

    static final byte[] SEGREDO = "segredo-de-webhook-de-teste-32-bytes!".getBytes(StandardCharsets.UTF_8);
    static final String PLANO = "premium-mensal-teste";
    static final long PRECO = 1990;

    @Autowired
    protected RepositorioDePlanos planos;
    @Autowired
    protected RepositorioDePedidos pedidos;
    @Autowired
    protected RepositorioDeAssinaturas assinaturas;
    @Autowired
    protected RepositorioDeEventosDePagamento eventos;
    @Autowired
    protected br.com.instadino.servidor.dominio.Repositorios.RepositorioDeLicencas licencas;

    protected void garantirPlano() {
        if (planos.findById(PLANO).isEmpty()) {
            planos.save(new Plano(PLANO, "Premium mensal (TESTE)", "Plano de teste do sandbox", "MENSAL", PRECO, "BRL", true));
        }
    }

    protected String ultimoEmail;

    protected String clienteEToken() throws Exception {
        garantirPlano();
        String email = emailUnico();
        ultimoEmail = email;
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        return entrar(email, SENHA_FORTE);
    }

    /** Entra de novo com o último cliente criado (o token de sessão pode ter expirado ao avançar o relógio). */
    protected String entrarDeNovo() throws Exception {
        return entrar(ultimoEmail, SENHA_FORTE);
    }

    protected UUID criarPedido(String token) throws Exception {
        MvcResult r = executar(comToken(postJson("/v1/checkout", Map.of("planoId", PLANO)), token));
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("Checkout falhou: " + r.getResponse().getStatus() + " " + r.getResponse().getContentAsString());
        }
        return UUID.fromString(corpo(r).get("pedidoId").asText());
    }

    protected String simular(String token, UUID pedido, String tipo) throws Exception {
        MvcResult r = executar(comToken(postJson("/v1/sandbox/pedidos/" + pedido + "/eventos/" + tipo, "{}"), token));
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("Simulação falhou: " + r.getResponse().getStatus() + " " + r.getResponse().getContentAsString());
        }
        return corpo(r).get("resultado").asText();
    }

    protected JsonNode assinatura(String token) throws Exception {
        return corpo(executar(obter("/v1/assinatura", token)));
    }

    protected boolean premium(String token) throws Exception {
        return assinatura(token).get("premiumVigente").asBoolean();
    }

    protected String corpoDeEvento(String id, String tipo, Instant criadoEm, UUID pedidoId, long valor, String moeda) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tipo", tipo);
        m.put("criadoEm", criadoEm.toString());
        m.put("pedidoId", pedidoId.toString());
        m.put("valorEmCentavos", valor);
        m.put("moeda", moeda);
        return json.writeValueAsString(m);
    }

    protected MvcResult webhook(String corpo, String cabecalho) throws Exception {
        var b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/webhooks/pagamento")
                .contentType("application/json").content(corpo);
        if (cabecalho != null) {
            b.header("X-Sandbox-Signature", cabecalho);
        }
        return executar(b);
    }

    protected MvcResult webhookAssinado(String corpo) throws Exception {
        return webhook(corpo, VerificadorDeAssinaturaHmac.assinar(SEGREDO, relogio.instant(), corpo));
    }

    protected String resultado(MvcResult r) throws Exception {
        return corpo(r).path("resultado").asText();
    }
}
