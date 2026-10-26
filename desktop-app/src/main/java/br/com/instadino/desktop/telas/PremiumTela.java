package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.Plano;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Comparação Free x Premium, estado da licença e situação da compra. */
final class PremiumTela extends TelaBase {

    /** Rótulo e se o recurso já existe nesta versão do aplicativo. */
    private record Recurso(String rotulo, boolean disponivelNestaVersao) {
    }

    private static final Map<Funcionalidade, Recurso> RECURSOS = new LinkedHashMap<>();

    static {
        RECURSOS.put(Funcionalidade.IMPORTACAO_ZIP, new Recurso("Importação do ZIP oficial", true));
        RECURSOS.put(Funcionalidade.DASHBOARD_SOCIAL, new Recurso("Painel social", true));
        RECURSOS.put(Funcionalidade.RELACIONAMENTOS_BASICO, new Recurso("Seguidores, seguindo, mútuos e não recíprocos", true));
        RECURSOS.put(Funcionalidade.BUSCA_BASICA, new Recurso("Busca e ordenação", true));
        RECURSOS.put(Funcionalidade.COMPARACAO_MANUAL_LISTAS, new Recurso("Comparação manual de duas listas", true));
        RECURSOS.put(Funcionalidade.EXPORTACAO_CSV_BASICA, new Recurso("Exportação CSV básica", true));
        RECURSOS.put(Funcionalidade.EXCLUSAO_DE_DADOS, new Recurso("Exclusão de dados locais", true));
        RECURSOS.put(Funcionalidade.HISTORICO_COMPLETO, new Recurso("Histórico completo de importações", true));
        RECURSOS.put(Funcionalidade.COMPARACAO_ENTRE_PERIODOS, new Recurso("Comparação entre períodos", true));
        RECURSOS.put(Funcionalidade.FILTROS_COMPOSTOS, new Recurso("Filtros compostos", true));
        RECURSOS.put(Funcionalidade.CSV_PERSONALIZADO, new Recurso("CSV personalizado", true));
        RECURSOS.put(Funcionalidade.GRAFICOS_HISTORICOS, new Recurso("Gráficos históricos", false));
        RECURSOS.put(Funcionalidade.RELATORIO_PDF, new Recurso("Relatórios em PDF", false));
        RECURSOS.put(Funcionalidade.FAVORITOS, new Recurso("Favoritos", false));
        RECURSOS.put(Funcionalidade.ETIQUETAS, new Recurso("Etiquetas", false));
        RECURSOS.put(Funcionalidade.NOTAS_PRIVADAS, new Recurso("Notas privadas", false));
        RECURSOS.put(Funcionalidade.MULTIPLOS_PERFIS, new Recurso("Múltiplos perfis locais", false));
        RECURSOS.put(Funcionalidade.BIBLIOTECA_DE_CONTEUDO, new Recurso("Biblioteca de conteúdo", false));
        RECURSOS.put(Funcionalidade.ANALISE_DE_ATIVIDADE, new Recurso("Análise de atividade", false));
        RECURSOS.put(Funcionalidade.ARQUIVO_DE_MENSAGENS, new Recurso("Arquivo de mensagens (opcional, com consentimento)", false));
        RECURSOS.put(Funcionalidade.BACKUP_PROTEGIDO, new Recurso("Backup local protegido", false));
    }

    private final VBox area = new VBox(16);

    PremiumTela(ContextoDaInterface ui) {
        super(ui, "Premium", "Compare os planos e veja a situação da sua licença.");
        corpo(area);
    }

    @Override
    public void aoExibir() {
        area.getChildren().clear();
        Plano plano = ui.local().politica().planoEfetivo();
        Label situacao = new Label(plano == Plano.PREMIUM ? "Plano atual: Premium" : "Plano atual: Free (gratuito, sem validade)");
        situacao.getStyleClass().add("titulo-cartao");
        VBox estado = Ui.cartaoDestaque(situacao,
                Ui.suave(plano == Plano.PREMIUM
                        ? "Sua licença foi validada. Os recursos Premium estão liberados."
                        : "Você usa o InstaDino gratuitamente. Seus dados locais nunca são bloqueados ao mudar de plano."),
                Ui.caixa(Ui.Tipo.INFO,
                        "A compra do plano Premium ainda não está habilitada. Nenhuma cobrança é feita por este aplicativo. "
                                + "Os preços serão exibidos aqui quando o serviço comercial estiver disponível."));
        area.getChildren().add(estado);

        TableView<Map.Entry<Funcionalidade, Recurso>> tabela = new TableView<>();
        tabela.getColumns().add(coluna("Recurso", e -> e.getValue().rotulo(), 360));
        tabela.getColumns().add(coluna("Free", e -> e.getKey().planoMinimo() == Plano.FREE ? "Incluído" : "—", 90));
        tabela.getColumns().add(coluna("Premium", e -> "Incluído", 90));
        tabela.getColumns().add(coluna("Nesta versão", e -> e.getValue().disponivelNestaVersao() ? "Disponível" : "Em desenvolvimento", 150));
        tabela.getItems().setAll(RECURSOS.entrySet());
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setFixedCellSize(42);
        tabela.setPrefHeight(46 + RECURSOS.size() * 42);
        tabela.setAccessibleText("Comparação entre os planos Free e Premium");
        area.getChildren().add(Ui.cartao(Ui.tituloDeCartao("Free e Premium"), tabela));
        area.getChildren().add(new HBox(Ui.pequeno("Os recursos “Em desenvolvimento” fazem parte do plano Premium, mas ainda não existem nesta versão do aplicativo.")));
    }

    private static TableColumn<Map.Entry<Funcionalidade, Recurso>, String> coluna(String titulo,
            java.util.function.Function<Map.Entry<Funcionalidade, Recurso>, String> valor, double largura) {
        TableColumn<Map.Entry<Funcionalidade, Recurso>, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        c.setPrefWidth(largura);
        return c;
    }
}
