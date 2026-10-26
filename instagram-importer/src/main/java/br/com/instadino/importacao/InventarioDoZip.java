package br.com.instadino.importacao;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resultado da inspeção do ZIP, sem extrair nada para o disco. */
public record InventarioDoZip(long tamanhoDoZip, List<EntradaInventariada> entradas, List<String> avisos) {

    public InventarioDoZip {
        entradas = List.copyOf(entradas);
        avisos = List.copyOf(avisos);
    }

    public Map<CategoriaDeDados, Integer> arquivosPorCategoria() {
        Map<CategoriaDeDados, Integer> mapa = new EnumMap<>(CategoriaDeDados.class);
        for (EntradaInventariada e : entradas) {
            mapa.merge(e.categoria(), 1, Integer::sum);
        }
        return mapa;
    }

    public boolean contem(CategoriaDeDados categoria) {
        return entradas.stream().anyMatch(e -> e.categoria() == categoria);
    }

    public List<CategoriaDeDados> categoriasSensiveisPresentes() {
        return arquivosPorCategoria().keySet().stream().filter(CategoriaDeDados::sensivel).toList();
    }
}
