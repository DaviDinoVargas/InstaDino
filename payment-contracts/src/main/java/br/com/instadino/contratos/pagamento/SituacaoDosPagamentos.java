package br.com.instadino.contratos.pagamento;

/** Resultado da interpretação da configuração de pagamentos. Qualquer ambiguidade resulta em "sem cobrança". */
public enum SituacaoDosPagamentos {
    /** Compras indisponíveis: nenhuma cobrança, nem de teste. */
    DESABILITADO,
    /** Compras de teste com o provedor simulado; nenhuma cobrança real. */
    SANDBOX_ATIVO,
    /** Cobrança real autorizada: habilitada, em produção, com credenciais completas e aprovação explícita. */
    PRODUCAO_ATIVA,
    /** Produção solicitada, mas incompleta: tratada como DESABILITADO, nunca como autorização para cobrar. */
    PRODUCAO_INCOMPLETA
}
