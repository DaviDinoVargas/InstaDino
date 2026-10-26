package br.com.instadino.contratos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfiguracaoDePagamentosTest {

    private static Map<String, String> env(String... pares) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return m;
    }

    @Test
    void semNenhumaConfiguracaoNaoHaCompras() {
        var c = ConfiguracaoDePagamentos.deAmbiente(Map.of());
        assertThat(c.situacao()).isEqualTo(SituacaoDosPagamentos.DESABILITADO);
        assertThat(c.comprasPermitidas()).isFalse();
        assertThat(c.cobrancaRealPermitida()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "TRUE", "True", "1", "yes", "sim", "enabled", "true ", "talvez"})
    void somenteOTextoExatoTrueHabilita(String valor) {
        var c = ConfiguracaoDePagamentos.deAmbiente(env("PAYMENTS_ENABLED", valor));
        if (valor.strip().equals("true")) {
            assertThat(c.habilitado()).isTrue();
        } else {
            assertThat(c.habilitado()).as("valor '%s'", valor).isFalse();
        }
    }

    @Test
    void modoAusenteOuDesconhecidoNuncaViraProducao() {
        assertThat(ConfiguracaoDePagamentos.deAmbiente(env("PAYMENTS_ENABLED", "true")).modo()).isEqualTo(ModoDePagamento.SANDBOX);
        assertThat(ConfiguracaoDePagamentos.deAmbiente(env("PAYMENTS_ENABLED", "true", "PAYMENTS_MODE", "prod-ish")).modo())
                .isEqualTo(ModoDePagamento.SANDBOX);
        assertThat(ConfiguracaoDePagamentos.deAmbiente(env("PAYMENTS_ENABLED", "true", "PAYMENTS_MODE", "")).modo()).isEqualTo(ModoDePagamento.SANDBOX);
    }

    @Test
    void sandboxPermiteComprasDeTesteSemCobrancaReal() {
        var c = ConfiguracaoDePagamentos.deAmbiente(env("PAYMENTS_ENABLED", "true", "PAYMENTS_MODE", "sandbox"));
        assertThat(c.situacao()).isEqualTo(SituacaoDosPagamentos.SANDBOX_ATIVO);
        assertThat(c.comprasPermitidas()).isTrue();
        assertThat(c.cobrancaRealPermitida()).isFalse();
    }

    @Test
    void desabilitadoComModoProducaoContinuaSemCompras() {
        var c = ConfiguracaoDePagamentos.deAmbiente(env("PAYMENTS_ENABLED", "false", "PAYMENTS_MODE", "production",
                "PAYMENTS_WEBHOOK_SECRET", "x".repeat(32), "PAYMENTS_PROVIDER_API_KEY", "y", "PAYMENTS_PRODUCTION_APPROVED", "true"));
        assertThat(c.situacao()).isEqualTo(SituacaoDosPagamentos.DESABILITADO);
        assertThat(c.cobrancaRealPermitida()).isFalse();
    }

    @Test
    void producaoIncompletaNuncaEAutorizacaoParaCobrar() {
        var base = new String[] {"PAYMENTS_ENABLED", "true", "PAYMENTS_MODE", "production"};
        var semNada = ConfiguracaoDePagamentos.deAmbiente(env(base));
        assertThat(semNada.situacao()).isEqualTo(SituacaoDosPagamentos.PRODUCAO_INCOMPLETA);
        assertThat(semNada.comprasPermitidas()).isFalse();
        assertThat(semNada.cobrancaRealPermitida()).isFalse();
        assertThat(semNada.pendenciasDeProducao()).containsExactly("PAYMENTS_WEBHOOK_SECRET", "PAYMENTS_PROVIDER_API_KEY", "PAYMENTS_PRODUCTION_APPROVED");

        // cada item faltando isoladamente impede a cobrança
        String[][] completos = {{"PAYMENTS_WEBHOOK_SECRET", "s".repeat(32)}, {"PAYMENTS_PROVIDER_API_KEY", "k"}, {"PAYMENTS_PRODUCTION_APPROVED", "true"}};
        for (int faltante = 0; faltante < 3; faltante++) {
            Map<String, String> m = env(base);
            for (int i = 0; i < 3; i++) {
                if (i != faltante) {
                    m.put(completos[i][0], completos[i][1]);
                }
            }
            var c = ConfiguracaoDePagamentos.deAmbiente(m);
            assertThat(c.cobrancaRealPermitida()).as("faltando %s", completos[faltante][0]).isFalse();
            assertThat(c.pendenciasDeProducao()).containsExactly(completos[faltante][0]);
        }
    }

    @Test
    void producaoCompletaEAprovadaPermiteCobranca() {
        var c = ConfiguracaoDePagamentos.deAmbiente(env("PAYMENTS_ENABLED", "true", "PAYMENTS_MODE", "production",
                "PAYMENTS_WEBHOOK_SECRET", "s".repeat(32), "PAYMENTS_PROVIDER_API_KEY", "k", "PAYMENTS_PRODUCTION_APPROVED", "true"));
        assertThat(c.situacao()).isEqualTo(SituacaoDosPagamentos.PRODUCAO_ATIVA);
        assertThat(c.cobrancaRealPermitida()).isTrue();
        assertThat(c.pendenciasDeProducao()).isEmpty();
    }

    @Test
    void aprovacaoSemOutrosItensNaoBasta() {
        var c = ConfiguracaoDePagamentos.deAmbiente(env("PAYMENTS_ENABLED", "true", "PAYMENTS_MODE", "production", "PAYMENTS_PRODUCTION_APPROVED", "true"));
        assertThat(c.cobrancaRealPermitida()).isFalse();
    }
}
