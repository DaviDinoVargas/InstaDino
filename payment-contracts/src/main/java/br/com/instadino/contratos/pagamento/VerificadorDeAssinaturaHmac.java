package br.com.instadino.contratos.pagamento;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifica assinaturas de webhook no esquema {@code t=<epoch segundos>,v1=<hex>}, em que {@code v1} é o
 * HMAC-SHA256 de {@code t + "." + corpoBruto} com o segredo do endpoint.
 *
 * <p>Este é o esquema comum a vários provedores. Os detalhes de cada provedor real (nome do cabeçalho, tolerância,
 * formato do evento) devem ser confirmados na documentação oficial antes de ativar a produção; ver
 * docs/payments/PROVIDER_DECISION.md.
 *
 * <p>A comparação é em tempo constante, o corpo deve ser o BRUTO recebido, e eventos com timestamp fora da
 * tolerância (passado ou futuro) são recusados para limitar a janela de repetição.
 */
public final class VerificadorDeAssinaturaHmac {

    private static final String ALGORITMO = "HmacSHA256";

    private final byte[] segredo;
    private final Duration tolerancia;

    public VerificadorDeAssinaturaHmac(byte[] segredo, Duration tolerancia) {
        if (segredo == null || segredo.length < 16) {
            throw new IllegalArgumentException("O segredo do webhook deve ter ao menos 16 bytes.");
        }
        this.segredo = segredo.clone();
        this.tolerancia = tolerancia;
    }

    public static String assinar(byte[] segredo, Instant instante, String corpoBruto) {
        long t = instante.getEpochSecond();
        return "t=" + t + ",v1=" + HexFormat.of().formatHex(hmac(segredo, t + "." + corpoBruto));
    }

    /** Lança {@link AssinaturaInvalidaException} se qualquer verificação falhar. */
    public void verificar(String cabecalho, String corpoBruto, Instant agora) {
        if (cabecalho == null || corpoBruto == null || cabecalho.length() > 1024) {
            throw new AssinaturaInvalidaException();
        }
        Long t = null;
        java.util.List<String> assinaturas = new java.util.ArrayList<>();
        for (String parte : cabecalho.split(",")) {
            int igual = parte.indexOf('=');
            if (igual <= 0) {
                continue;
            }
            String chave = parte.substring(0, igual).strip();
            String valor = parte.substring(igual + 1).strip();
            if (chave.equals("t") && t == null) {
                try {
                    t = Long.parseLong(valor);
                } catch (NumberFormatException e) {
                    throw new AssinaturaInvalidaException();
                }
            } else if (chave.equals("v1")) {
                assinaturas.add(valor);
            }
        }
        if (t == null || assinaturas.isEmpty()) {
            throw new AssinaturaInvalidaException();
        }
        long diferenca = Math.abs(agora.getEpochSecond() - t);
        if (diferenca > tolerancia.getSeconds()) {
            throw new AssinaturaInvalidaException();
        }
        byte[] esperado = hmac(segredo, t + "." + corpoBruto);
        boolean valida = false;
        for (String candidata : assinaturas) {
            byte[] recebido;
            try {
                recebido = HexFormat.of().parseHex(candidata);
            } catch (IllegalArgumentException e) {
                continue;
            }
            // sem curto-circuito: examina todas as candidatas
            valida |= MessageDigest.isEqual(esperado, recebido);
        }
        if (!valida) {
            throw new AssinaturaInvalidaException();
        }
    }

    private static byte[] hmac(byte[] segredo, String mensagem) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo, ALGORITMO));
            return mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponível.", e);
        }
    }
}
