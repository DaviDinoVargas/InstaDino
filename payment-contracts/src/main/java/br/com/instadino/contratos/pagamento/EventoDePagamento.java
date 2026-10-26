package br.com.instadino.contratos.pagamento;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento já autenticado. {@code criadoEm} é o instante informado pelo provedor e serve para ordenar eventos;
 * {@code valorEmCentavos} é conferido contra o preço do pedido, que vem do servidor.
 */
public record EventoDePagamento(String id, TipoDeEventoDePagamento tipo, Instant criadoEm, UUID pedidoId, long valorEmCentavos,
                                String moeda, String referenciaDoProvedor) {
}
