package br.com.instadino.licenciamento;

/** Erro devolvido pela API comercial, com código estável e mensagem segura para exibir. */
public final class ErroDaApi extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String codigo;

    public ErroDaApi(int status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public int status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }
}
