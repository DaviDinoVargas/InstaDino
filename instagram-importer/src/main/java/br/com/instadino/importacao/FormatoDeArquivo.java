package br.com.instadino.importacao;

import java.util.Locale;
import java.util.Set;

/** Formato inferido pela extensão. O conteúdo nunca é executado nem interpretado como código. */
public enum FormatoDeArquivo {
    JSON, HTML, MIDIA, OUTRO;

    private static final Set<String> MIDIAS = Set.of("jpg", "jpeg", "png", "webp", "heic", "gif", "mp4", "mov", "m4v", "webm", "mp3", "m4a", "aac", "wav");

    public static FormatoDeArquivo deCaminho(String caminho) {
        int ponto = caminho.lastIndexOf('.');
        if (ponto < 0 || ponto < caminho.lastIndexOf('/')) {
            return OUTRO;
        }
        String extensao = caminho.substring(ponto + 1).toLowerCase(Locale.ROOT);
        if (extensao.equals("json")) {
            return JSON;
        }
        if (extensao.equals("html") || extensao.equals("htm")) {
            return HTML;
        }
        return MIDIAS.contains(extensao) ? MIDIA : OUTRO;
    }
}
