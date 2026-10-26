package br.com.instadino.contratos.pagamento;

/** Eventos comerciais tratados pelo servidor, já traduzidos a partir do formato do provedor. */
public enum TipoDeEventoDePagamento {
    PAGAMENTO_APROVADO,
    PAGAMENTO_RECUSADO,
    RENOVACAO_APROVADA,
    ASSINATURA_CANCELADA,
    PAGAMENTO_REEMBOLSADO,
    DISPUTA_ABERTA
}
