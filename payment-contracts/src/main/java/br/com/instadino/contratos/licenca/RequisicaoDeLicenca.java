package br.com.instadino.contratos.licenca;

/** Pedido de ativação ou renovação de licença para um dispositivo. O identificador é gerado pelo próprio aplicativo. */
public record RequisicaoDeLicenca(String idDoDispositivo, String nomeDoDispositivo) {
}
