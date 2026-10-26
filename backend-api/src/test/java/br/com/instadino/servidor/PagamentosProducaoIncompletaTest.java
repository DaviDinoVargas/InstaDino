package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.servidor.dominio.Plano;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePlanos;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Produção solicitada, mas incompleta: precisa ser tratada como "sem cobrança". */
@TestPropertySource(properties = {"PAYMENTS_ENABLED=true", "PAYMENTS_MODE=production"})
class PagamentosProducaoIncompletaTest extends TesteBase {

    @Autowired
    RepositorioDePlanos planos;

    @Test
    void configuracaoIncompletaDeProducaoNuncaAutorizaCobranca() throws Exception {
        planos.save(new Plano("plano-prod-incompleto", "Premium", "Plano", "MENSAL", 1990, "BRL", true));
        String email = emailUnico();
        criarConta(email, SENHA_FORTE, Papel.CLIENTE, true);
        String token = entrar(email, SENHA_FORTE);

        var catalogo = corpo(executar(MockMvcRequestBuilders.get("/v1/planos")));
        assertThat(catalogo.get("situacao").asText()).isEqualTo("PRODUCAO_INCOMPLETA");
        assertThat(catalogo.get("comprasHabilitadas").asBoolean()).isFalse();

        MvcResult r = executar(comToken(postJson("/v1/checkout", Map.of("planoId", "plano-prod-incompleto")), token));
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(corpo(r).get("codigo").asText()).isEqualTo("COMPRAS_INDISPONIVEIS");
    }
}
