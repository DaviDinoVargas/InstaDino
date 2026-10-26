package br.com.instadino.licenciamento;

import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.contratos.licenca.PayloadDeLicenca;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Emite licenças de teste exatamente no formato do servidor, com uma chave de teste gerada na hora. */
final class LicencasDeTeste {

    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    final KeyPair par;
    final String kid;

    LicencasDeTeste(String kid) {
        this.kid = kid;
        try {
            this.par = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    ChavesConfiaveis confiaveis() {
        return ChavesConfiaveis.de(kid, par.getPublic());
    }

    PayloadDeLicenca payload(UUID titular, String dispositivo, Instant emitida, int diasDeValidade, int diasDeTolerancia) {
        return new PayloadDeLicenca(1, UUID.randomUUID(), PayloadDeLicenca.PRODUTO, "PREMIUM", titular, dispositivo, emitida,
                emitida.plusSeconds(diasDeValidade * 86400L), emitida.plusSeconds((diasDeValidade + diasDeTolerancia) * 86400L), "ASSINATURA");
    }

    LicencaAssinada assinar(PayloadDeLicenca p) {
        return assinarBytes(bytes(p), par);
    }

    static byte[] bytes(PayloadDeLicenca p) {
        try {
            return JSON.writeValueAsBytes(p);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    LicencaAssinada assinarBytes(byte[] payload, KeyPair chave) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(chave.getPrivate());
            s.update(payload);
            Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
            return new LicencaAssinada(b64.encodeToString(payload), b64.encodeToString(s.sign()), kid, "Ed25519");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String texto(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
