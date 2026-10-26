package br.com.instadino.importacao;

/** Recebe o progresso real da importação (nunca uma animação sem relação com o trabalho). */
@FunctionalInterface
public interface OuvinteDeProgresso {

    OuvinteDeProgresso NENHUM = (etapa, atual, total) -> { };

    void progresso(String etapa, long atual, long total);
}
