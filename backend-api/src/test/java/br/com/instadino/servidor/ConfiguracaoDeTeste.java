package br.com.instadino.servidor;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ConfiguracaoDeTeste {

    @Bean
    @Primary
    RelogioMutavel relogioDeTeste() {
        return new RelogioMutavel();
    }

    @Bean
    @Primary
    EmailEmMemoria emailDeTeste() {
        return new EmailEmMemoria();
    }

    @Bean
    Clock relogioAlias(RelogioMutavel r) {
        return r;
    }
}
