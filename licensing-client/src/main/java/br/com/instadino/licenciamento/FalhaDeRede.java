package br.com.instadino.licenciamento;

/** Não foi possível falar com o serviço comercial (sem internet, servidor fora do ar, tempo esgotado). */
public final class FalhaDeRede extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FalhaDeRede() {
        super("Não foi possível conectar ao serviço comercial. Verifique sua conexão e tente novamente.");
    }
}
