package br.com.instadino.contratos.pagamento;

import java.util.List;

/**
 * Catálogo e situação das compras. {@code mensagem} é exibida ao usuário quando as compras estão indisponíveis.
 * {@code modo} é "SANDBOX" quando compras de teste estão ativas: nesse caso NENHUMA cobrança real ocorre.
 */
public record RespostaDeCatalogo(List<PlanoComercial> planos, SituacaoDosPagamentos situacao, boolean comprasHabilitadas, String mensagem) {
}
