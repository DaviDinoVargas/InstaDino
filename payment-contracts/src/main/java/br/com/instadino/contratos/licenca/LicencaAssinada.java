package br.com.instadino.contratos.licenca;

/**
 * Envelope de licença: {@code payload} é o JSON de {@link PayloadDeLicenca} em Base64 URL sem preenchimento;
 * {@code assinatura} é a assinatura Ed25519 dos bytes decodificados do payload; {@code kid} identifica a chave pública.
 */
public record LicencaAssinada(String payload, String assinatura, String kid, String algoritmo) {

    public static final String ALGORITMO = "Ed25519";
}
