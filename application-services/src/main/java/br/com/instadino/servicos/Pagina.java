package br.com.instadino.servicos;

import java.util.List;

/** Uma página de resultados. {@code pagina} começa em 0. */
public record Pagina<T>(List<T> itens, int total, int pagina, int tamanho) {

    public Pagina {
        itens = List.copyOf(itens);
    }

    public int totalDePaginas() {
        return tamanho <= 0 ? 0 : (total + tamanho - 1) / tamanho;
    }
}
