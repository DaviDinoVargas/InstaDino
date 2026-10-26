package br.com.instadino.contratos.conta;

import java.time.Instant;
import java.util.UUID;

public record SessaoAtiva(UUID id, String nomeDoDispositivo, Instant criadaEm, Instant ultimoUso, Instant expiraEm, boolean atual) {
}
