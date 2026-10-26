package br.com.instadino.nucleo.acesso;

/** Fornece o estado da licença já validado (assinatura, titularidade, validade e tolerância). */
@FunctionalInterface
public interface FonteDeLicenca {
    EstadoDaLicenca estadoAtual();
}
