package br.com.instadino.nucleo.acesso;

/** Lançada quando uma funcionalidade não está disponível. A mensagem é segura para exibição ao usuário. */
public final class AcessoNegadoException extends RuntimeException {

    public enum Motivo { PLANO_INSUFICIENTE, CONSENTIMENTO_AUSENTE }

    private static final long serialVersionUID = 1L;

    private final Funcionalidade funcionalidade;
    private final Motivo motivo;

    public AcessoNegadoException(Funcionalidade funcionalidade, Motivo motivo) {
        super(mensagem(motivo));
        this.funcionalidade = funcionalidade;
        this.motivo = motivo;
    }

    private static String mensagem(Motivo motivo) {
        return switch (motivo) {
            case PLANO_INSUFICIENTE -> "Este recurso está disponível no plano Premium.";
            case CONSENTIMENTO_AUSENTE -> "Este módulo exige que você o habilite explicitamente.";
        };
    }

    public Funcionalidade funcionalidade() {
        return funcionalidade;
    }

    public Motivo motivo() {
        return motivo;
    }
}
