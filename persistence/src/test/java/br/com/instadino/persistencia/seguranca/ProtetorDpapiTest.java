package br.com.instadino.persistencia.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Só executa em Windows, onde a DPAPI existe. Em outros sistemas o teste é ignorado (não é aprovado por omissão). */
@EnabledOnOs(OS.WINDOWS)
class ProtetorDpapiTest {

    @Test
    void protegeERevelaOSegredoEOBlobNaoContemOTextoEmClaro() {
        var protetor = new ProtetorDpapi();
        byte[] segredo = "SEGREDO_DE_TESTE_DPAPI_0123456789".getBytes(StandardCharsets.UTF_8);
        byte[] blob = protetor.proteger(segredo);
        assertThat(new String(blob, StandardCharsets.ISO_8859_1)).doesNotContain("SEGREDO_DE_TESTE_DPAPI");
        assertThat(protetor.revelar(blob)).isEqualTo(segredo);
        assertThat(protetor.seguroParaProducao()).isTrue();
    }
}
