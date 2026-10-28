package br.com.instadino.contratos.pagamento;

/** Plano à venda. O preço vem sempre do servidor; o cliente nunca informa nem calcula valores. */
public record PlanoComercial(String id, String nome, String descricao, String periodicidade, long precoEmCentavos, String moeda) {
}
