package br.com.instadino.importacao;

import java.util.EnumSet;
import java.util.Set;

/** Escolhas do usuário para a importação. Categorias sensíveis só são processadas se autorizadas aqui. */
public record ConfiguracaoDeImportacao(Set<CategoriaDeDados> categoriasSensiveisAutorizadas, boolean incluirAtividade,
                                       boolean incluirConteudo) {

    /** Atividade e conteúdo são incluídos por padrão. */
    public ConfiguracaoDeImportacao(Set<CategoriaDeDados> categoriasSensiveisAutorizadas) {
        this(categoriasSensiveisAutorizadas, true, true);
    }


    public ConfiguracaoDeImportacao {
        categoriasSensiveisAutorizadas = categoriasSensiveisAutorizadas.isEmpty()
                ? Set.of() : Set.copyOf(EnumSet.copyOf(categoriasSensiveisAutorizadas));
    }

    public static ConfiguracaoDeImportacao padrao() {
        return new ConfiguracaoDeImportacao(Set.of());
    }

    public ConfiguracaoDeImportacao semAtividadeNemConteudo() {
        return new ConfiguracaoDeImportacao(categoriasSensiveisAutorizadas, false, false);
    }

    public boolean permite(CategoriaDeDados categoria) {
        if (ImportadorDeExportacao.ATIVIDADES.contains(categoria)) {
            return incluirAtividade;
        }
        if (ImportadorDeExportacao.CONTEUDOS.contains(categoria)) {
            return incluirConteudo;
        }
        return !categoria.sensivel() || categoriasSensiveisAutorizadas.contains(categoria);
    }
}
