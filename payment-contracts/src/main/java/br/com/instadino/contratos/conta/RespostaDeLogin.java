package br.com.instadino.contratos.conta;

import java.time.Instant;

/** O token de sessão é opaco e mostrado uma única vez; o servidor guarda apenas o hash. */
public record RespostaDeLogin(String token, Instant expiraEm, ResumoDaConta conta) {

    @Override
    public String toString() {
        return "RespostaDeLogin[token=***, expiraEm=" + expiraEm + ", conta=" + conta + "]";
    }
}
