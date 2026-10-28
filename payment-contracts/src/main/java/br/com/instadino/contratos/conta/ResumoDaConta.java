package br.com.instadino.contratos.conta;

import java.util.UUID;

public record ResumoDaConta(UUID id, String email, String nome, Papel papel, boolean emailVerificado) {
}
