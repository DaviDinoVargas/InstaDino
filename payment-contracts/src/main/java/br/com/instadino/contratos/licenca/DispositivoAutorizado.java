package br.com.instadino.contratos.licenca;

import java.time.Instant;
import java.util.UUID;

public record DispositivoAutorizado(UUID id, String nome, Instant ativadoEm) {
}
