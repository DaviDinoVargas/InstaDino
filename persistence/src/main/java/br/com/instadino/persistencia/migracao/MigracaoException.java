package br.com.instadino.persistencia.migracao;

import br.com.instadino.persistencia.BancoException;

/** Falha de migração. Nunca deixa o banco parcialmente migrado (cada migração é atômica). */
public final class MigracaoException extends BancoException {

    private static final long serialVersionUID = 1L;

    public MigracaoException(String mensagem) {
        super(mensagem);
    }

    public MigracaoException(String mensagem, int codigoSqlite) {
        super(mensagem, codigoSqlite);
    }
}
