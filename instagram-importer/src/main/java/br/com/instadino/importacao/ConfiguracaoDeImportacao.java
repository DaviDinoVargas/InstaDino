package br.com.instadino.importacao;

import java.util.EnumSet;
import java.util.Set;

/** Escolhas do usuário para a importação. Categorias sensíveis só são processadas se autorizadas aqui. */
public record ConfiguracaoDeImportacao(Set<CategoriaDeDados> categoriasSensiveisAutorizadas) {

    public ConfiguracaoDeImportacao {
        categoriasSensiveisAutorizadas = categoriasSensiveisAutorizadas.isEmpty()
                ? Set.of() : Set.copyOf(EnumSet.copyOf(categoriasSensiveisAutorizadas));
    }

    public static ConfiguracaoDeImportacao padrao() {
        return new ConfiguracaoDeImportacao(Set.of());
    }

    public boolean permite(CategoriaDeDados categoria) {
        return !categoria.sensivel() || categoriasSensiveisAutorizadas.contains(categoria);
    }
}
