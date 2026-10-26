package br.com.instadino.servidor.seguranca;

import br.com.instadino.contratos.conta.Papel;
import java.util.UUID;

/** Identidade autenticada de uma requisição, derivada da sessão no servidor (nunca de dados do cliente). */
public record PrincipalAutenticado(UUID contaId, UUID sessaoId, Papel papel) {
}
