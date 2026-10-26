package br.com.instadino.servidor.config;

import br.com.instadino.contratos.pagamento.ConfiguracaoDePagamentos;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Recusa a inicialização insegura: exige um perfil explícito e aplica {@link AvaliadorDeConfiguracaoSegura} em produção. */
@Component
class VerificadorNaInicializacao {

    private final Environment ambiente;
    private final PropriedadesDoServidor propriedades;
    private final ConfiguracaoDePagamentos pagamentos;
    private final boolean swaggerHabilitado;

    VerificadorNaInicializacao(Environment ambiente, PropriedadesDoServidor propriedades, ConfiguracaoDePagamentos pagamentos,
                               @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerHabilitado) {
        this.ambiente = ambiente;
        this.propriedades = propriedades;
        this.pagamentos = pagamentos;
        this.swaggerHabilitado = swaggerHabilitado;
    }

    @EventListener(ApplicationReadyEvent.class)
    void verificar() {
        boolean producao = ambiente.acceptsProfiles(Profiles.of("producao"));
        boolean conhecido = producao || ambiente.acceptsProfiles(Profiles.of("desenvolvimento", "teste"));
        if (!conhecido) {
            throw new IllegalStateException("Defina o perfil: SPRING_PROFILES_ACTIVE=producao ou desenvolvimento. "
                    + "Sem perfil explícito o servidor não inicia.");
        }
        boolean tls = Boolean.parseBoolean(ambiente.getProperty("server.ssl.enabled", "false"))
                || propriedades.seguranca().tlsTerminadoNoProxy();
        List<String> problemas = AvaliadorDeConfiguracaoSegura.avaliar(new AvaliadorDeConfiguracaoSegura.Instantaneo(
                producao, ambiente.getProperty("spring.datasource.url"), tls,
                propriedades.licenca().chavePrivadaArquivo() != null, propriedades.semeadura().habilitada(),
                propriedades.seguranca().emailRealConfigurado(), swaggerHabilitado, pagamentos,
                Boolean.parseBoolean(ambiente.getProperty("spring.h2.console.enabled", "false"))));
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Configuração insegura para produção:\n - " + String.join("\n - ", problemas));
        }
    }
}
