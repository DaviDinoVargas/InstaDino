package br.com.instadino.contratos.pagamento;

/** A autenticidade do evento não pôde ser comprovada. A mensagem é genérica de propósito. */
public final class AssinaturaInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssinaturaInvalidaException() {
        super("Assinatura do evento inválida.");
    }
}
