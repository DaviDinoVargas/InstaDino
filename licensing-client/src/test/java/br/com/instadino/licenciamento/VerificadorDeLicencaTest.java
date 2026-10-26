package br.com.instadino.licenciamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.contratos.licenca.PayloadDeLicenca;
import br.com.instadino.licenciamento.VerificadorDeLicenca.Motivo;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificadorDeLicencaTest {

    private static final Instant AGORA = Instant.parse("2026-03-01T12:00:00Z");
    private static final UUID TITULAR = UUID.randomUUID();
    private static final String DISPOSITIVO = "dispositivo-de-teste-0001";

    private final LicencasDeTeste emissor = new LicencasDeTeste("k1");
    private final VerificadorDeLicenca verificador = new VerificadorDeLicenca(emissor.confiaveis());

    private PayloadDeLicenca valida() {
        return emissor.payload(TITULAR, DISPOSITIVO, AGORA, 7, 7);
    }

    private VerificadorDeLicenca.Resultado verificar(LicencaAssinada l) {
        return verificador.verificar(l, TITULAR, DISPOSITIVO, AGORA, null);
    }

    private void assertFree(VerificadorDeLicenca.Resultado r, Motivo esperado) {
        assertThat(r.motivo()).isEqualTo(esperado);
        assertThat(r.estado().planoEfetivo()).as("nenhuma falha pode conceder Premium").isEqualTo(Plano.FREE);
    }

    // ---- caminho feliz e prazos ----

    @Test
    void licencaValidaConcedePremiumAtivo() {
        var r = verificar(emissor.assinar(valida()));
        assertThat(r.motivo()).isEqualTo(Motivo.OK);
        assertThat(r.estado().plano()).isEqualTo(Plano.PREMIUM);
        assertThat(r.estado().situacao()).isEqualTo(SituacaoDaLicenca.ATIVA);
        assertThat(r.estado().premiumVigente()).isTrue();
    }

    @Test
    void aposAValidadeEntraEmToleranciaOfflineEDepoisExpira() {
        var licenca = emissor.assinar(valida());
        var tolerancia = verificador.verificar(licenca, TITULAR, DISPOSITIVO, AGORA.plus(Duration.ofDays(8)), null);
        assertThat(tolerancia.estado().situacao()).isEqualTo(SituacaoDaLicenca.TOLERANCIA_OFFLINE);
        assertThat(tolerancia.estado().premiumVigente()).isTrue();

        var expirada = verificador.verificar(licenca, TITULAR, DISPOSITIVO, AGORA.plus(Duration.ofDays(15)), null);
        assertThat(expirada.motivo()).isEqualTo(Motivo.EXPIRADA);
        assertThat(expirada.estado().situacao()).isEqualTo(SituacaoDaLicenca.EXPIRADA);
        assertThat(expirada.estado().premiumVigente()).isFalse();
        assertThat(expirada.estado().planoEfetivo()).isEqualTo(Plano.FREE);
    }

    // ---- assinatura ----

    @Test
    void qualquerAlteracaoNoPayloadInvalidaAAssinatura() {
        var original = emissor.assinar(valida());
        byte[] bytes = Base64.getUrlDecoder().decode(original.payload());
        for (int i = 0; i < bytes.length; i += 7) {
            byte[] adulterado = bytes.clone();
            adulterado[i] ^= 0x01;
            var l = new LicencaAssinada(Base64.getUrlEncoder().withoutPadding().encodeToString(adulterado), original.assinatura(), "k1", "Ed25519");
            assertThat(verificar(l).motivo()).as("byte %d", i).isIn(Motivo.ASSINATURA_INVALIDA, Motivo.FORMATO_INVALIDO);
            assertThat(verificar(l).estado().premiumVigente()).isFalse();
        }
    }

    @Test
    void aSubstituicaoDeDatasParaEstenderAValidadeFalha() {
        var original = emissor.assinar(valida());
        String texto = LicencasDeTeste.texto(Base64.getUrlDecoder().decode(original.payload()));
        String estendido = texto.replace("\"validaAte\":\"2026-03-08T12:00:00Z\"", "\"validaAte\":\"2036-03-08T12:00:00Z\"")
                .replace("\"toleranciaOfflineAte\":\"2026-03-15T12:00:00Z\"", "\"toleranciaOfflineAte\":\"2036-03-15T12:00:00Z\"");
        assertThat(estendido).isNotEqualTo(texto);
        var l = new LicencaAssinada(Base64.getUrlEncoder().withoutPadding().encodeToString(estendido.getBytes(StandardCharsets.UTF_8)),
                original.assinatura(), "k1", "Ed25519");
        assertFree(verificador.verificar(l, TITULAR, DISPOSITIVO, AGORA.plus(Duration.ofDays(60)), null), Motivo.ASSINATURA_INVALIDA);
    }

    @Test
    void assinaturaAdulteradaOuTruncadaFalha() {
        var original = emissor.assinar(valida());
        byte[] sig = Base64.getUrlDecoder().decode(original.assinatura());
        sig[0] ^= 0x01;
        assertFree(verificar(new LicencaAssinada(original.payload(), Base64.getUrlEncoder().withoutPadding().encodeToString(sig), "k1", "Ed25519")), Motivo.ASSINATURA_INVALIDA);
        assertFree(verificar(new LicencaAssinada(original.payload(), original.assinatura().substring(10), "k1", "Ed25519")), Motivo.ASSINATURA_INVALIDA);
        assertFree(verificar(new LicencaAssinada(original.payload(), "", "k1", "Ed25519")), Motivo.ASSINATURA_INVALIDA);
    }

    @Test
    void licencaAssinadaPorOutraChaveMesmoUsandoOKidConfiavelFalha() throws Exception {
        var atacante = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var forjada = emissor.assinarBytes(LicencasDeTeste.bytes(valida()), atacante);
        assertFree(verificar(forjada), Motivo.ASSINATURA_INVALIDA);
    }

    @Test
    void kidDesconhecidoEAlgoritmosDiferentesDeEd25519SaoRecusados() {
        var original = emissor.assinar(valida());
        assertFree(verificar(new LicencaAssinada(original.payload(), original.assinatura(), "k2", "Ed25519")), Motivo.CHAVE_DESCONHECIDA);
        assertFree(verificar(new LicencaAssinada(original.payload(), original.assinatura(), null, "Ed25519")), Motivo.FORMATO_INVALIDO);
        for (String algoritmo : new String[] {"none", "HS256", "RS256", "ed25519", "Ed448", ""}) {
            assertFree(verificar(new LicencaAssinada(original.payload(), original.assinatura(), "k1", algoritmo)), Motivo.ALGORITMO_NAO_SUPORTADO);
        }
    }

    @Test
    void entradasNulasMalformadasOuGigantesNaoQuebramEDaoFree() {
        assertFree(verificar(null), Motivo.FORMATO_INVALIDO);
        assertFree(verificar(new LicencaAssinada(null, null, null, null)), Motivo.FORMATO_INVALIDO);
        assertFree(verificar(new LicencaAssinada("***não-é-base64***", "abc", "k1", "Ed25519")), Motivo.FORMATO_INVALIDO);
        assertFree(verificar(new LicencaAssinada("A".repeat(50_000), "AAAA", "k1", "Ed25519")), Motivo.FORMATO_INVALIDO);
        assertFree(verificar(new LicencaAssinada("", "", "k1", "Ed25519")), Motivo.FORMATO_INVALIDO);
    }

    @Test
    void payloadComAssinaturaValidaMasSemCamposObrigatoriosEFormatoInvalido() {
        assertFree(verificar(emissor.assinarBytes("{}".getBytes(StandardCharsets.UTF_8), emissor.par)), Motivo.FORMATO_INVALIDO);
        assertFree(verificar(emissor.assinarBytes("não é json".getBytes(StandardCharsets.UTF_8), emissor.par)), Motivo.FORMATO_INVALIDO);
        assertFree(verificar(emissor.assinarBytes("{\"versao\":1,\"plano\":\"PREMIUM\"}".getBytes(StandardCharsets.UTF_8), emissor.par)), Motivo.FORMATO_INVALIDO);
    }

    // ---- campos do payload (assinados corretamente, mas inadequados a este cliente) ----

    @Test
    void produtoPlanoVersaoTitularEDispositivoSaoConferidos() {
        var base = valida();
        assertFree(verificar(emissor.assinar(new PayloadDeLicenca(1, base.licencaId(), "outro-produto", "PREMIUM", TITULAR, DISPOSITIVO,
                base.emitidaEm(), base.validaAte(), base.toleranciaOfflineAte(), "X"))), Motivo.PRODUTO_INCORRETO);
        assertFree(verificar(emissor.assinar(new PayloadDeLicenca(1, base.licencaId(), PayloadDeLicenca.PRODUTO, "FREE", TITULAR, DISPOSITIVO,
                base.emitidaEm(), base.validaAte(), base.toleranciaOfflineAte(), "X"))), Motivo.PLANO_INVALIDO);
        assertFree(verificar(emissor.assinar(new PayloadDeLicenca(2, base.licencaId(), PayloadDeLicenca.PRODUTO, "PREMIUM", TITULAR, DISPOSITIVO,
                base.emitidaEm(), base.validaAte(), base.toleranciaOfflineAte(), "X"))), Motivo.VERSAO_NAO_SUPORTADA);
        assertFree(verificador.verificar(emissor.assinar(base), UUID.randomUUID(), DISPOSITIVO, AGORA, null), Motivo.TITULAR_DIFERENTE);
        assertFree(verificador.verificar(emissor.assinar(base), TITULAR, "outro-dispositivo-9999", AGORA, null), Motivo.DISPOSITIVO_DIFERENTE);
    }

    @Test
    void datasIncoerentesEEmitidaNoFuturoSaoRecusadas() {
        var base = valida();
        assertFree(verificar(emissor.assinar(new PayloadDeLicenca(1, base.licencaId(), PayloadDeLicenca.PRODUTO, "PREMIUM", TITULAR, DISPOSITIVO,
                AGORA, AGORA.minusSeconds(10), AGORA.plusSeconds(100), "X"))), Motivo.FORMATO_INVALIDO);
        assertFree(verificar(emissor.assinar(new PayloadDeLicenca(1, base.licencaId(), PayloadDeLicenca.PRODUTO, "PREMIUM", TITULAR, DISPOSITIVO,
                AGORA, AGORA.plusSeconds(1000), AGORA.plusSeconds(100), "X"))), Motivo.FORMATO_INVALIDO);
        var futura = emissor.payload(TITULAR, DISPOSITIVO, AGORA.plus(Duration.ofHours(2)), 7, 7);
        assertFree(verificar(emissor.assinar(futura)), Motivo.EMITIDA_NO_FUTURO);
        var quaseAgora = emissor.payload(TITULAR, DISPOSITIVO, AGORA.plus(Duration.ofMinutes(4)), 7, 7);
        assertThat(verificar(emissor.assinar(quaseAgora)).motivo()).as("folga de 5 minutos para pequenas diferenças de relógio").isEqualTo(Motivo.OK);
    }

    // ---- relógio ----

    @Test
    void retrocessoDoRelogioInvalidaALicencaAteSerRenovada() {
        var licenca = emissor.assinar(valida());
        Instant visto = AGORA.plus(Duration.ofDays(20));
        // o usuário volta o relógio para "estender" a licença que já expirou
        var r = verificador.verificar(licenca, TITULAR, DISPOSITIVO, AGORA.plus(Duration.ofDays(2)), visto);
        assertFree(r, Motivo.RELOGIO_RETROCEDEU);
        var pequenoAjuste = verificador.verificar(licenca, TITULAR, DISPOSITIVO, visto.minus(Duration.ofMinutes(3)), visto);
        assertThat(pequenoAjuste.motivo()).as("pequenas correções automáticas de relógio são toleradas").isNotEqualTo(Motivo.RELOGIO_RETROCEDEU);
    }

    @Test
    void camposDesconhecidosNoPayloadNaoQuebramACompatibilidade() throws Exception {
        String comExtra = LicencasDeTeste.texto(LicencasDeTeste.bytes(valida())).replaceFirst("\\{", "{\"novoCampo\":\"x\",");
        var r = verificar(emissor.assinarBytes(comExtra.getBytes(StandardCharsets.UTF_8), emissor.par));
        assertThat(r.motivo()).isEqualTo(Motivo.OK);
    }
}
