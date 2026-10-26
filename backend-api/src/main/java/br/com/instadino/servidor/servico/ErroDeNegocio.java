package br.com.instadino.servidor.servico;

import org.springframework.http.HttpStatus;

/** Erro previsto da API: status HTTP, código estável e mensagem segura para o usuário. */
public class ErroDeNegocio extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String codigo;

    public ErroDeNegocio(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }
}
