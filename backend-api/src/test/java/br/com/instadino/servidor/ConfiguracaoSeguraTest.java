package br.com.instadino.servidor;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.contratos.pagamento.ConfiguracaoDePagamentos;
import br.com.instadino.servidor.config.AvaliadorDeConfiguracaoSegura;
import br.com.instadino.servidor.config.AvaliadorDeConfiguracaoSegura.Instantaneo;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfiguracaoSeguraTest {

    private static final ConfiguracaoDePagamentos SEM_PAGAMENTOS = ConfiguracaoDePagamentos.deAmbiente(Map.of());

    private static Instantaneo producaoBoa() {
        return new Instantaneo(true, "jdbc:postgresql://banco:5432/instadino", true, true, false, true, false, SEM_PAGAMENTOS, false);
    }

    @Test
    void producaoCorretaNaoTemProblemas() {
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(producaoBoa())).isEmpty();
    }

    @Test
    void foraDeProducaoNadaEExigido() {
        var dev = new Instantaneo(false, "jdbc:h2:mem:x", false, false, true, false, true,
                ConfiguracaoDePagamentos.deAmbiente(Map.of("PAYMENTS_ENABLED", "true", "PAYMENTS_MODE", "sandbox")), true);
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(dev)).isEmpty();
    }

    @Test
    void cadaViolacaoEmProducaoEDetectada() {
        var b = producaoBoa();
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(new Instantaneo(true, "jdbc:h2:file:x", true, true, false, true, false, SEM_PAGAMENTOS, false)))
                .anyMatch(p -> p.contains("PostgreSQL"));
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(new Instantaneo(true, b.urlDoBanco(), false, true, false, true, false, SEM_PAGAMENTOS, false)))
                .anyMatch(p -> p.contains("TLS"));
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(new Instantaneo(true, b.urlDoBanco(), true, false, false, true, false, SEM_PAGAMENTOS, false)))
                .anyMatch(p -> p.contains("chave privada"));
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(new Instantaneo(true, b.urlDoBanco(), true, true, true, true, false, SEM_PAGAMENTOS, false)))
                .anyMatch(p -> p.contains("semeadura"));
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(new Instantaneo(true, b.urlDoBanco(), true, true, false, false, false, SEM_PAGAMENTOS, false)))
                .anyMatch(p -> p.contains("e-mail"));
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(new Instantaneo(true, b.urlDoBanco(), true, true, false, true, true, SEM_PAGAMENTOS, false)))
                .anyMatch(p -> p.contains("Swagger"));
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(new Instantaneo(true, b.urlDoBanco(), true, true, false, true, false, SEM_PAGAMENTOS, true)))
                .anyMatch(p -> p.contains("console"));
    }

    @Test
    void pagamentosEmProducaoSaoBloqueadosEmTodasAsVariantes() {
        var base = producaoBoa();
        var sandbox = ConfiguracaoDePagamentos.deAmbiente(Map.of("PAYMENTS_ENABLED", "true", "PAYMENTS_MODE", "sandbox"));
        var incompleta = ConfiguracaoDePagamentos.deAmbiente(Map.of("PAYMENTS_ENABLED", "true", "PAYMENTS_MODE", "production"));
        var completa = ConfiguracaoDePagamentos.deAmbiente(Map.of("PAYMENTS_ENABLED", "true", "PAYMENTS_MODE", "production",
                "PAYMENTS_WEBHOOK_SECRET", "s".repeat(32), "PAYMENTS_PROVIDER_API_KEY", "k", "PAYMENTS_PRODUCTION_APPROVED", "true"));
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(com(base, sandbox))).anyMatch(p -> p.contains("sandbox"));
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(com(base, incompleta))).anyMatch(p -> p.contains("incompleta"));
        assertThat(AvaliadorDeConfiguracaoSegura.avaliar(com(base, completa))).anyMatch(p -> p.contains("adaptador do provedor"));
    }

    private static Instantaneo com(Instantaneo b, ConfiguracaoDePagamentos p) {
        return new Instantaneo(b.producao(), b.urlDoBanco(), b.tlsDiretoOuNoProxy(), b.chaveDeLicencaConfigurada(), b.semeaduraHabilitada(),
                b.emailRealConfigurado(), b.documentacaoDaApiExposta(), p, b.consoleDoBancoHabilitado());
    }
}
