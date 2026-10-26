package br.com.instadino.nucleo.acesso;

/**
 * Catálogo de funcionalidades com identificadores estáveis.
 *
 * <p>O {@link #id()} é persistido e comparado em testes; não deve ser alterado depois de publicado.
 * Funcionalidades {@link #sensivel() sensíveis} exigem consentimento explícito independentemente do plano.
 */
public enum Funcionalidade {
    // Plano Free
    IMPORTACAO_ZIP("importacao.zip", Plano.FREE, false),
    DASHBOARD_SOCIAL("dashboard.social", Plano.FREE, false),
    RELACIONAMENTOS_BASICO("relacionamentos.basico", Plano.FREE, false),
    BUSCA_BASICA("busca.basica", Plano.FREE, false),
    FILTROS_BASICOS("filtros.basicos", Plano.FREE, false),
    COMPARACAO_MANUAL_LISTAS("comparacao.manual", Plano.FREE, false),
    EXPORTACAO_CSV_BASICA("exportacao.csv.basica", Plano.FREE, false),
    EXCLUSAO_DE_DADOS("dados.exclusao", Plano.FREE, false),

    // Plano Premium
    HISTORICO_COMPLETO("historico.completo", Plano.PREMIUM, false),
    COMPARACAO_ENTRE_PERIODOS("comparacao.periodos", Plano.PREMIUM, false),
    GRAFICOS_HISTORICOS("graficos.historicos", Plano.PREMIUM, false),
    RELATORIO_PDF("relatorio.pdf", Plano.PREMIUM, false),
    CSV_PERSONALIZADO("exportacao.csv.personalizado", Plano.PREMIUM, false),
    FILTROS_COMPOSTOS("filtros.compostos", Plano.PREMIUM, false),
    PESQUISA_AVANCADA("pesquisa.avancada", Plano.PREMIUM, false),
    FAVORITOS("favoritos", Plano.PREMIUM, false),
    ETIQUETAS("etiquetas", Plano.PREMIUM, false),
    NOTAS_PRIVADAS("notas.privadas", Plano.PREMIUM, false),
    MULTIPLOS_PERFIS("perfis.multiplos", Plano.PREMIUM, false),
    BIBLIOTECA_DE_CONTEUDO("conteudo.biblioteca", Plano.PREMIUM, false),
    ANALISE_DE_ATIVIDADE("atividade.analise", Plano.PREMIUM, false),
    EXPLORADOR_DE_DADOS("dados.explorador", Plano.PREMIUM, false),
    PAINEIS_PERSONALIZADOS("paineis.personalizados", Plano.PREMIUM, false),
    BACKUP_PROTEGIDO("backup.protegido", Plano.PREMIUM, false),
    RELATORIOS_COMPARATIVOS("relatorios.comparativos", Plano.PREMIUM, false),

    // Módulos sensíveis: plano Premium + consentimento explícito
    ARQUIVO_DE_MENSAGENS("mensagens.arquivo", Plano.PREMIUM, true);

    private final String id;
    private final Plano planoMinimo;
    private final boolean sensivel;

    Funcionalidade(String id, Plano planoMinimo, boolean sensivel) {
        this.id = id;
        this.planoMinimo = planoMinimo;
        this.sensivel = sensivel;
    }

    public String id() {
        return id;
    }

    public Plano planoMinimo() {
        return planoMinimo;
    }

    public boolean sensivel() {
        return sensivel;
    }
}
