package br.com.instadino.persistencia;

/**
 * Falha de acesso ao banco local. A mensagem é segura para exibição: nunca contém a URL de conexão, a chave
 * nem dados armazenados. O código de erro do SQLite fica disponível para diagnóstico.
 */
public class BancoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int codigoSqlite;

    public BancoException(String mensagem) {
        super(mensagem);
        this.codigoSqlite = 0;
    }

    public BancoException(String mensagem, int codigoSqlite) {
        super(mensagem);
        this.codigoSqlite = codigoSqlite;
    }

    public int codigoSqlite() {
        return codigoSqlite;
    }
}
