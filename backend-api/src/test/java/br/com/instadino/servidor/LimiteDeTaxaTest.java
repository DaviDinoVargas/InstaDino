package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties = {"instadino.seguranca.limite-de-requisicoes-por-janela=4", "instadino.seguranca.janela-em-minutos=5"})
class LimiteDeTaxaTest extends TesteBase {

    @Test
    void excedidoOLimiteAsRequisicoesRecebem429ComRetryAfter() throws Exception {
        int ultimoStatus = 0;
        MvcResult bloqueada = null;
        for (int i = 0; i < 6; i++) {
            MvcResult r = executar(postJson("/v1/sessoes", Map.of("email", emailUnico(), "senha", "qualquer-senha-123", "idDoDispositivo", dispositivo())));
            ultimoStatus = r.getResponse().getStatus();
            if (ultimoStatus == 429) {
                bloqueada = r;
                break;
            }
        }
        assertThat(ultimoStatus).isEqualTo(429);
        assertThat(bloqueada.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(corpo(bloqueada).get("codigo").asText()).isEqualTo("MUITAS_REQUISICOES");
    }

    @Test
    void ALimitacaoNaoAfetaEndpointsAutenticadosNemOutrosCaminhos() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/status")).getResponse().getStatus()).isEqualTo(200);
        }
    }

    @Test
    void ALimitacaoRecuperaDepoisDaJanela() throws Exception {
        // a janela usa o relógio da aplicação
        for (int i = 0; i < 6; i++) {
            executar(postJson("/v1/senha/recuperacao", Map.of("email", emailUnico())));
        }
        assertThat(executar(postJson("/v1/senha/recuperacao", Map.of("email", emailUnico()))).getResponse().getStatus()).isEqualTo(429);
        relogio.avancar(Duration.ofMinutes(6));
        assertThat(executar(postJson("/v1/senha/recuperacao", Map.of("email", emailUnico()))).getResponse().getStatus()).isEqualTo(202);
    }
}
