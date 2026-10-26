package br.com.instadino.contratos.pagamento;

import java.util.UUID;

/** {@code urlDoCheckout} é a página hospedada pelo provedor; o InstaDino nunca coleta dados de cartão. */
public record RespostaDeCheckout(UUID pedidoId, String urlDoCheckout, boolean sandbox) {
}
