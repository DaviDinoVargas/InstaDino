package br.com.instadino.contratos.conta;

/** Corpo padrão de erro: um código estável para o cliente e uma mensagem segura para o usuário. */
public record ErroApi(String codigo, String mensagem) {
}
