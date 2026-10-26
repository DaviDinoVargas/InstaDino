package br.com.instadino.servidor.config;

import br.com.instadino.contratos.pagamento.ConfiguracaoDePagamentos;
import br.com.instadino.contratos.pagamento.ModoDePagamento;
import br.com.instadino.contratos.pagamento.ProvedorPagamento;
import br.com.instadino.servidor.pagamento.ProvedorNaoImplementado;
import br.com.instadino.servidor.pagamento.ProvedorSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class ConfiguracaoDePagamentoDoServidor {

    /**
     * Escolhe o provedor. Sandbox usa o segredo de PAYMENTS_WEBHOOK_SECRET ou, na falta dele, um segredo aleatório que
     * existe só nesta execução (ninguém de fora consegue assinar eventos). Produção não tem adaptador: falha fechada.
     */
    @Bean
    ProvedorPagamento provedorDePagamento(ConfiguracaoDePagamentos configuracao, Environment ambiente, Clock relogio, ObjectMapper json) {
        if (configuracao.modo() == ModoDePagamento.PRODUCAO) {
            return new ProvedorNaoImplementado();
        }
        String definido = ambiente.getProperty("PAYMENTS_WEBHOOK_SECRET");
        byte[] segredo;
        if (definido != null && definido.strip().length() >= 16) {
            segredo = definido.strip().getBytes(StandardCharsets.UTF_8);
        } else {
            segredo = new byte[32];
            new SecureRandom().nextBytes(segredo);
        }
        return new ProvedorSandbox(segredo, relogio, json);
    }
}
