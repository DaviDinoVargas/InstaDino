package br.com.instadino.servicos;

import java.time.Instant;

/** Uma conta exibida em uma lista, com o endereço do perfil reconstruído a partir do nome validado. */
public record ContaListada(String usuario, String urlDoPerfil, Instant registradaEm) {
}
