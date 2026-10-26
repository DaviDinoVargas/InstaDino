package br.com.instadino.nucleo.acesso;

import java.util.Objects;

/**
 * Política central de acesso. A interface gráfica pode ocultar ou sinalizar recursos, mas os casos de uso
 * devem chamar {@link #exigir(Funcionalidade)} para que a regra também seja aplicada na camada de aplicação.
 */
public final class PoliticaDeAcesso {

    private final FonteDeLicenca licenca;
    private final RegistroDeConsentimento consentimentos;

    public PoliticaDeAcesso(FonteDeLicenca licenca, RegistroDeConsentimento consentimentos) {
        this.licenca = Objects.requireNonNull(licenca, "licenca");
        this.consentimentos = Objects.requireNonNull(consentimentos, "consentimentos");
    }

    public Plano planoEfetivo() {
        return licenca.estadoAtual().planoEfetivo();
    }

    public boolean permite(Funcionalidade funcionalidade) {
        return negacao(funcionalidade) == null;
    }

    public void exigir(Funcionalidade funcionalidade) {
        AcessoNegadoException.Motivo motivo = negacao(funcionalidade);
        if (motivo != null) {
            throw new AcessoNegadoException(funcionalidade, motivo);
        }
    }

    private AcessoNegadoException.Motivo negacao(Funcionalidade funcionalidade) {
        Objects.requireNonNull(funcionalidade, "funcionalidade");
        if (funcionalidade.planoMinimo() == Plano.PREMIUM && planoEfetivo() != Plano.PREMIUM) {
            return AcessoNegadoException.Motivo.PLANO_INSUFICIENTE;
        }
        if (funcionalidade.sensivel() && !consentimentos.concedido(funcionalidade)) {
            return AcessoNegadoException.Motivo.CONSENTIMENTO_AUSENTE;
        }
        return null;
    }
}
