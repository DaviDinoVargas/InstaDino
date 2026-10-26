package br.com.instadino.persistencia.seguranca;

/** O sistema não oferece um mecanismo seguro para proteger a chave do banco. */
public final class ProtecaoIndisponivelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtecaoIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public ProtecaoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
