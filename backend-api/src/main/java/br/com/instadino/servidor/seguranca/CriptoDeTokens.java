package br.com.instadino.servidor.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Geração e hash de tokens opacos. Só o hash SHA-256 é persistido; o valor original é mostrado uma vez. */
public final class CriptoDeTokens {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CriptoDeTokens() {
    }

    /** 256 bits de entropia, em Base64 URL sem preenchimento. */
    public static String novoToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
