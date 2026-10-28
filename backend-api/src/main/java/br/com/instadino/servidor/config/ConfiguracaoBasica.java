package br.com.instadino.servidor.config;

import br.com.instadino.contratos.pagamento.ConfiguracaoDePagamentos;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class ConfiguracaoBasica {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    /**
     * Argon2id com os parâmetros mínimos recomendados pela OWASP (19 MiB de memória, 2 iterações, 1 de paralelismo),
     * sal de 16 bytes e hash de 32 bytes. O sal é individual e embutido no hash.
     */
    @Bean
    PasswordEncoder codificadorDeSenhas() {
        return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    }

    /** Lê PAYMENTS_ENABLED, PAYMENTS_MODE e demais variáveis; qualquer ausência significa sem cobrança. */
    @Bean
    ConfiguracaoDePagamentos configuracaoDePagamentos(Environment ambiente) {
        Map<String, String> valores = new HashMap<>();
        for (String chave : new String[] {"PAYMENTS_ENABLED", "PAYMENTS_MODE", "PAYMENTS_WEBHOOK_SECRET", "PAYMENTS_PROVIDER_API_KEY",
            "PAYMENTS_PRODUCTION_APPROVED"}) {
            String v = ambiente.getProperty(chave);
            if (v != null) {
                valores.put(chave, v);
            }
        }
        return ConfiguracaoDePagamentos.deAmbiente(valores);
    }
}
