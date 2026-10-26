package br.com.instadino.nucleo.acesso;

/** Situação de uma licença após validação criptográfica e temporal. */
public enum SituacaoDaLicenca {
    /** Nenhuma licença presente: plano Free. */
    AUSENTE,
    /** Licença válida e dentro da validade. */
    ATIVA,
    /** Servidor indisponível, mas dentro do período de tolerância offline. */
    TOLERANCIA_OFFLINE,
    /** Validade e tolerância esgotadas. */
    EXPIRADA,
    /** Revogada pelo servidor. */
    REVOGADA,
    /** Assinatura, produto ou titularidade inválidos. */
    INVALIDA
}
