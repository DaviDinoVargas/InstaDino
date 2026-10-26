package br.com.instadino.servicos;

import java.time.Instant;

/**
 * Parâmetros de uma consulta às listas. Busca e ordenação são do plano Free; o intervalo de datas e a opção
 * "somente com data" são filtros compostos (Premium).
 */
public record ConsultaDeRelacionamentos(VisaoDeRelacionamento visao, String busca, Ordenacao ordenacao, int pagina, int tamanho,
                                        Instant desde, Instant ate, boolean somenteComData) {

    public enum Ordenacao { ALFABETICA, ALFABETICA_INVERSA, MAIS_RECENTES, MAIS_ANTIGOS }

    public static final int TAMANHO_MAXIMO_DE_PAGINA = 500;

    public ConsultaDeRelacionamentos {
        if (visao == null) {
            throw new IllegalArgumentException("A visão é obrigatória.");
        }
        busca = busca == null ? "" : busca.strip();
        ordenacao = ordenacao == null ? Ordenacao.ALFABETICA : ordenacao;
        pagina = Math.max(pagina, 0);
        tamanho = Math.max(1, Math.min(tamanho, TAMANHO_MAXIMO_DE_PAGINA));
    }

    public static ConsultaDeRelacionamentos de(VisaoDeRelacionamento visao) {
        return new ConsultaDeRelacionamentos(visao, "", Ordenacao.ALFABETICA, 0, 100, null, null, false);
    }

    public ConsultaDeRelacionamentos comBusca(String texto) {
        return new ConsultaDeRelacionamentos(visao, texto, ordenacao, 0, tamanho, desde, ate, somenteComData);
    }

    public ConsultaDeRelacionamentos comOrdenacao(Ordenacao nova) {
        return new ConsultaDeRelacionamentos(visao, busca, nova, pagina, tamanho, desde, ate, somenteComData);
    }

    public ConsultaDeRelacionamentos naPagina(int nova) {
        return new ConsultaDeRelacionamentos(visao, busca, ordenacao, nova, tamanho, desde, ate, somenteComData);
    }

    public ConsultaDeRelacionamentos comPeriodo(Instant novoDesde, Instant novoAte) {
        return new ConsultaDeRelacionamentos(visao, busca, ordenacao, 0, tamanho, novoDesde, novoAte, somenteComData);
    }

    public ConsultaDeRelacionamentos comSomenteComData(boolean valor) {
        return new ConsultaDeRelacionamentos(visao, busca, ordenacao, 0, tamanho, desde, ate, valor);
    }

    /** Verdadeiro se a consulta usa filtros que só o Premium oferece. */
    public boolean usaFiltrosCompostos() {
        return desde != null || ate != null || somenteComData;
    }
}
