package br.com.instadino.analise.exportacao;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Escritor CSV pensado para abrir em planilhas no Brasil (separador ponto e vírgula, UTF-8 com BOM).
 *
 * <p>Segurança: textos que começam com {@code = + - @}, tabulação ou retorno de carro são prefixados com
 * apóstrofo para que planilhas não os interpretem como fórmulas (CSV injection); caracteres de controle são
 * removidos. Valores numéricos são gravados sem alteração.
 */
public final class ExportadorCsv {

    private static final char SEPARADOR = ';';

    private ExportadorCsv() {
    }

    public static void escrever(OutputStream saida, List<String> cabecalho, Iterable<? extends List<?>> linhas) throws IOException {
        Writer w = new OutputStreamWriter(saida, StandardCharsets.UTF_8);
        w.write('﻿');
        escreverLinha(w, cabecalho);
        for (List<?> linha : linhas) {
            escreverLinha(w, linha);
        }
        w.flush();
    }

    private static void escreverLinha(Writer w, List<?> celulas) throws IOException {
        boolean primeira = true;
        for (Object celula : celulas) {
            if (!primeira) {
                w.write(SEPARADOR);
            }
            primeira = false;
            w.write(formatar(celula));
        }
        w.write("\r\n");
    }

    static String formatar(Object valor) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof Number || valor instanceof Boolean) {
            return valor.toString();
        }
        String bruto = valor.toString();
        String texto = limparControles(bruto);
        if (pareceFormula(bruto)) {
            texto = "'" + texto;
        }
        boolean precisaAspas = texto.indexOf(SEPARADOR) >= 0 || texto.indexOf('"') >= 0 || texto.indexOf('\n') >= 0
                || texto.indexOf('\r') >= 0 || texto.startsWith(" ") || texto.endsWith(" ");
        if (precisaAspas) {
            return '"' + texto.replace("\"", "\"\"") + '"';
        }
        return texto;
    }

    /**
     * Verdadeiro se uma planilha poderia interpretar o texto como fórmula: o primeiro caractere começa com
     * tabulação ou retorno de carro, ou o primeiro caractere significativo (depois de espaços e controles) é
     * {@code = + - @}.
     */
    private static boolean pareceFormula(String bruto) {
        if (bruto.isEmpty()) {
            return false;
        }
        char primeiro = bruto.charAt(0);
        if (primeiro == '\t' || primeiro == '\r') {
            return true;
        }
        for (int i = 0; i < bruto.length(); i++) {
            char c = bruto.charAt(i);
            if (c > ' ' && c != 0x7F) {
                return "=+-@".indexOf(c) >= 0;
            }
        }
        return false;
    }

    /** Remove caracteres de controle, preservando quebras de linha e tabulação reduzidas a espaço. */
    private static String limparControles(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(c == '\t' ? ' ' : c);
            } else if (c >= 0x20 && c != 0x7F) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
