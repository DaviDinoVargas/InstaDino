package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.servidor.dominio.Plano;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePedidos;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePlanos;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Configuração padrão: PAYMENTS_ENABLED ausente. Nada pode ser comprado nem simulado. */
class PagamentosDesabilitadosTest extends TesteBase {

    @Autowired
    RepositorioDePlanos planos;
    @Autowired
    RepositorioDePedidos pedidos;

    private String token() throws Exception {
        if (planos.findById("plano-desabilitado").isEmpty()) {
            planos.save(new Plano("plano-desabilitado", "Premium", "Plano", "MENSAL", 1990, "BRL", true));
        }
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        return entrar(email, SENHA_FORTE);
    }

    @Test
    void catalogoInformaQueAsComprasNaoEstaoHabilitadas() throws Exception {
        MvcResult r = executar(MockMvcRequestBuilders.get("/v1/planos"));
        assertThat(corpo(r).get("situacao").asText()).isEqualTo("DESABILITADO");
        assertThat(corpo(r).get("comprasHabilitadas").asBoolean()).isFalse();
        assertThat(corpo(r).get("mensagem").asText()).contains("ainda não está habilitada").contains("Nenhuma cobrança");
    }

    @Test
    void checkoutEDeProducaoSaoIndisponiveisESemEfeitoColateral() throws Exception {
        String token = token();
        long antes = pedidos.count();
        MvcResult r = executar(comToken(postJson("/v1/checkout", Map.of("planoId", "plano-desabilitado")), token));
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(corpo(r).get("codigo").asText()).isEqualTo("COMPRAS_INDISPONIVEIS");
        assertThat(corpo(r).get("mensagem").asText()).contains("ainda não está habilitada");
        assertThat(pedidos.count()).as("nenhum pedido foi criado").isEqualTo(antes);
    }

    @Test
    void simulacaoDeSandboxNaoExisteQuandoPagamentosEstaoDesabilitados() throws Exception {
        String token = token();
        MvcResult r = executar(comToken(postJson("/v1/sandbox/pedidos/" + UUID.randomUUID() + "/eventos/PAGAMENTO_APROVADO", "{}"), token));
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void webhookNaoAceitaNadaSemProvedorConfigurado() throws Exception {
        var b = MockMvcRequestBuilders.post("/v1/webhooks/pagamento").contentType("application/json").content("{}").header("X-Sandbox-Signature", "t=1,v1=00");
        assertThat(executar(b).getResponse().getStatus()).isEqualTo(400);
    }
}
