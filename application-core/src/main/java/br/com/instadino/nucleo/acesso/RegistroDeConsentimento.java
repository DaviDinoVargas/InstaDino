package br.com.instadino.nucleo.acesso;

/** Armazena consentimentos explícitos do usuário para módulos sensíveis. Padrão: nenhum consentimento. */
public interface RegistroDeConsentimento {
    boolean concedido(Funcionalidade funcionalidade);
}
