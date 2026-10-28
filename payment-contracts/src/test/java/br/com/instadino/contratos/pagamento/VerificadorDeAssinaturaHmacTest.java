package br.com.instadino.contratos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VerificadorDeAssinaturaHmacTest {

    private static final byte[] SEGREDO = "segredo-de-teste-com-32-bytes-ok!".getBytes(StandardCharsets.UTF_8);
    private static final Instant AGORA = Instant.parse("2026-01-01T12:00:00Z");
    private static final String CORPO = "{\"id\":\"evt_1\",\"tipo\":\"PAGAMENTO_APROVADO\"}";

    private final VerificadorDeAssinaturaHmac verificador = new VerificadorDeAssinaturaHmac(SEGREDO, Duration.ofMinutes(5));

    @Test
    void aceitaAssinaturaValida() {
        String cabecalho = VerificadorDeAssinaturaHmac.assinar(SEGREDO, AGORA, CORPO);
        assertThatCode(() -> verificador.verificar(cabecalho, CORPO, AGORA)).doesNotThrowAnyException();
    }

    @Test
    void rejeitaCorpoAdulteradoPorUmCaractere() {
        String cabecalho = VerificadorDeAssinaturaHmac.assinar(SEGREDO, AGORA, CORPO);
        assertThatThrownBy(() -> verificador.verificar(cabecalho, CORPO.replace("APROVADO", "APROVADA"), AGORA))
                .isInstanceOf(AssinaturaInvalidaException.class);
    }

    @Test
    void rejeitaSegredoErrado() {
        String cabecalho = VerificadorDeAssinaturaHmac.assinar("outro-segredo-com-mais-de-16".getBytes(StandardCharsets.UTF_8), AGORA, CORPO);
        assertThatThrownBy(() -> verificador.verificar(cabecalho, CORPO, AGORA)).isInstanceOf(AssinaturaInvalidaException.class);
    }

    @Test
    void rejeitaTimestampAntigoOuFuturoMasAceitaNaTolerancia() {
        String antigo = VerificadorDeAssinaturaHmac.assinar(SEGREDO, AGORA.minusSeconds(301), CORPO);
        String futuro = VerificadorDeAssinaturaHmac.assinar(SEGREDO, AGORA.plusSeconds(301), CORPO);
        String limite = VerificadorDeAssinaturaHmac.assinar(SEGREDO, AGORA.minusSeconds(300), CORPO);
        assertThatThrownBy(() -> verificador.verificar(antigo, CORPO, AGORA)).isInstanceOf(AssinaturaInvalidaException.class);
        assertThatThrownBy(() -> verificador.verificar(futuro, CORPO, AGORA)).isInstanceOf(AssinaturaInvalidaException.class);
        assertThatCode(() -> verificador.verificar(limite, CORPO, AGORA)).doesNotThrowAnyException();
    }

    @Test
    void trocarOTimestampInvalidaAAssinatura() {
        String cabecalho = VerificadorDeAssinaturaHmac.assinar(SEGREDO, AGORA.minusSeconds(100), CORPO);
        String adulterado = cabecalho.replaceFirst("t=\\d+", "t=" + AGORA.getEpochSecond());
        assertThatThrownBy(() -> verificador.verificar(adulterado, CORPO, AGORA)).isInstanceOf(AssinaturaInvalidaException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "lixo", "t=abc,v1=00", "t=1", "v1=abcd", "t=1,v1=zz", "t=,v1=", ",,,", "t=9999999999999999999999,v1=00"})
    void rejeitaCabecalhosMalformados(String cabecalho) {
        assertThatThrownBy(() -> verificador.verificar(cabecalho, CORPO, AGORA)).isInstanceOf(AssinaturaInvalidaException.class);
    }

    @Test
    void rejeitaCabecalhoOuCorpoNulosEGigantes() {
        assertThatThrownBy(() -> verificador.verificar(null, CORPO, AGORA)).isInstanceOf(AssinaturaInvalidaException.class);
        assertThatThrownBy(() -> verificador.verificar("t=1,v1=00", null, AGORA)).isInstanceOf(AssinaturaInvalidaException.class);
        assertThatThrownBy(() -> verificador.verificar("t=1,v1=" + "0".repeat(2000), CORPO, AGORA)).isInstanceOf(AssinaturaInvalidaException.class);
    }

    @Test
    void aceitaVariasAssinaturasSeUmaForValida() {
        String valido = VerificadorDeAssinaturaHmac.assinar(SEGREDO, AGORA, CORPO);
        String comExtra = valido + ",v1=" + "ab".repeat(32);
        assertThatCode(() -> verificador.verificar(comExtra, CORPO, AGORA)).doesNotThrowAnyException();
    }

    @Test
    void segredoCurtoEProibido() {
        assertThatThrownBy(() -> new VerificadorDeAssinaturaHmac(new byte[8], Duration.ofMinutes(5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void objetosSensiveisNaoExpoemSegredosNoToString() {
        assertThat(new br.com.instadino.contratos.conta.RequisicaoDeRedefinicao("tok123", "SenhaSecreta!1").toString()).doesNotContain("tok123", "SenhaSecreta");
        assertThat(new br.com.instadino.contratos.conta.RespostaDeLogin("tokenSecreto", Instant.EPOCH, null).toString()).doesNotContain("tokenSecreto");
        assertThat(new br.com.instadino.contratos.conta.RequisicaoDeToken("abc").toString()).doesNotContain("abc");
    }
}
