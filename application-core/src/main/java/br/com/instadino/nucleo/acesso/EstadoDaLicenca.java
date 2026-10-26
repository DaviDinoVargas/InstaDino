package br.com.instadino.nucleo.acesso;

import java.util.Objects;

/**
 * Resultado da validação de uma licença. Só deve ser produzido por um validador que verificou a
 * assinatura do servidor; nunca a partir de preferências ou arquivos editáveis.
 */
public record EstadoDaLicenca(Plano plano, SituacaoDaLicenca situacao) {

    public static final EstadoDaLicenca GRATUITO = new EstadoDaLicenca(Plano.FREE, SituacaoDaLicenca.AUSENTE);

    public EstadoDaLicenca {
        Objects.requireNonNull(plano, "plano");
        Objects.requireNonNull(situacao, "situacao");
    }

    /** Verdadeiro somente quando a licença concede o plano Premium agora. */
    public boolean premiumVigente() {
        return plano == Plano.PREMIUM
                && (situacao == SituacaoDaLicenca.ATIVA || situacao == SituacaoDaLicenca.TOLERANCIA_OFFLINE);
    }

    public Plano planoEfetivo() {
        return premiumVigente() ? Plano.PREMIUM : Plano.FREE;
    }
}
