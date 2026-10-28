package br.com.instadino.contratos.pagamento;

import java.time.Instant;
import java.util.UUID;

public record ItemDoHistorico(UUID pedidoId, String planoId, long valorEmCentavos, String moeda, String situacao, Instant criadoEm) {
}
