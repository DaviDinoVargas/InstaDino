package br.com.instadino.desktop.telas;

import br.com.instadino.analise.social.ComparacaoDeImportacoes;
import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ResumoDaImportacao;
import br.com.instadino.servicos.ServicoDeHistorico;
import java.util.List;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Histórico de importações; comparação entre períodos é recurso Premium. */
final class HistoricoTela extends TelaBase {

    private final VBox area = new VBox(16);

    HistoricoTela(ContextoDaInterface ui) {
        super(ui, "Histórico", "Importações anteriores e diferenças observadas entre elas.");
        corpo(area);
    }

    @Override
    public void aoExibir() {
        area.getChildren().clear();
        List<ResumoDaImportacao> lista = ui.local().historico().listar();
        if (lista.isEmpty()) {
            area.getChildren().add(Ui.cartao(Ui.estadoVazio("Sem importações", "Quando você importar uma exportação, ela aparecerá aqui.",
                    Ui.primario("Importar exportação", () -> ui.roteador().ir(Destino.IMPORTACAO)))));
            return;
        }
        boolean premium = ui.local().politica().permite(Funcionalidade.HISTORICO_COMPLETO);
        if (!premium) {
            area.getChildren().add(Ui.caixa(Ui.Tipo.INFO,
                    "No plano Free apenas a importação mais recente é mantida. O histórico completo e a comparação entre períodos fazem parte do plano Premium."));
        }
        TableView<ResumoDaImportacao> tabela = tabela(lista);
        area.getChildren().add(Ui.cartao(Ui.tituloDeCartao("Importações"), tabela));
        area.getChildren().add(Ui.secundario("Excluir importação selecionada", () -> excluir(tabela.getSelectionModel().getSelectedItem())));
        area.getChildren().add(grafico());
        area.getChildren().add(comparacao(lista));
    }

    /** Gráfico de evolução (Premium): uma observação por importação, nunca uma série contínua. */
    private VBox grafico() {
        boolean permitido = ui.local().politica().permite(Funcionalidade.GRAFICOS_HISTORICOS);
        VBox cartao = Ui.cartao(Ui.tituloDeCartao(permitido ? "Evolução entre importações" : "Evolução entre importações (Premium)"));
        if (!permitido) {
            cartao.getChildren().addAll(Ui.suave("Veja como seguidores, contas seguidas e relações mútuas variam entre as suas exportações."),
                    Ui.primario("Conhecer o Premium", () -> ui.roteador().ir(Destino.PREMIUM)));
            return cartao;
        }
        List<ServicoDeHistorico.PontoDaSerie> completos = ui.local().historico().serieHistorica().stream()
                .filter(p -> p.seguidores() != null && p.seguindo() != null).toList();
        if (completos.size() < 2) {
            cartao.getChildren().add(Ui.suave("Importe ao menos duas exportações com as listas de seguidores e de seguidos para ver a evolução."));
            return cartao;
        }
        CategoryAxis eixoX = new CategoryAxis();
        NumberAxis eixoY = new NumberAxis();
        eixoY.setForceZeroInRange(false);
        eixoY.setLabel("Contas");
        LineChart<String, Number> grafico = new LineChart<>(eixoX, eixoY);
        grafico.setAnimated(false);
        grafico.setCreateSymbols(true);
        grafico.setLegendVisible(true);
        grafico.setPrefHeight(300);
        grafico.setAccessibleText("Gráfico de linhas com seguidores, contas seguidas e relações mútuas em cada importação");
        XYChart.Series<String, Number> seguidores = new XYChart.Series<>();
        seguidores.setName("Seguidores");
        XYChart.Series<String, Number> seguindo = new XYChart.Series<>();
        seguindo.setName("Seguindo");
        XYChart.Series<String, Number> mutuos = new XYChart.Series<>();
        mutuos.setName("Mútuos");
        java.util.Set<String> usados = new java.util.HashSet<>();
        for (ServicoDeHistorico.PontoDaSerie p : completos) {
            String rotulo = Formatos.dataHora(p.importadaEm());
            while (!usados.add(rotulo)) {
                rotulo = rotulo + " ";
            }
            seguidores.getData().add(new XYChart.Data<>(rotulo, p.seguidores()));
            seguindo.getData().add(new XYChart.Data<>(rotulo, p.seguindo()));
            mutuos.getData().add(new XYChart.Data<>(rotulo, p.mutuos()));
        }
        grafico.getData().addAll(seguidores, seguindo, mutuos);
        cartao.getChildren().add(grafico);
        cartao.getChildren().add(Ui.pequeno("Cada ponto é o que a exportação continha na data da importação. A linha apenas liga as observações: "
                + "não mostra o que aconteceu entre elas nem a data exata de uma mudança."));
        return cartao;
    }

    private TableView<ResumoDaImportacao> tabela(List<ResumoDaImportacao> lista) {
        TableView<ResumoDaImportacao> t = new TableView<>();
        t.getColumns().add(coluna("Data", r -> Formatos.dataHora(r.importadaEm()), 170));
        t.getColumns().add(coluna("Arquivo", r -> r.nomeDoArquivo() == null ? "—" : r.nomeDoArquivo(), 220));
        t.getColumns().add(coluna("Origem", ResumoDaImportacao::origem, 200));
        t.getColumns().add(coluna("Válidos", r -> Formatos.inteiro(r.registrosValidos()), 90));
        t.getColumns().add(coluna("Inválidos", r -> Formatos.inteiro(r.registrosInvalidos()), 90));
        t.getColumns().add(coluna("Categorias", r -> Formatos.inteiro(r.categorias().size()), 100));
        t.getColumns().add(coluna("Leitor", ResumoDaImportacao::versaoDoParser, 80));
        t.getItems().setAll(lista);
        t.setFixedCellSize(42);
        t.setPrefHeight(Math.min(420, 46 + lista.size() * 42));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setAccessibleText("Histórico de importações");
        return t;
    }

    private static TableColumn<ResumoDaImportacao, String> coluna(String titulo, java.util.function.Function<ResumoDaImportacao, String> valor, double largura) {
        TableColumn<ResumoDaImportacao, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        c.setPrefWidth(largura);
        return c;
    }

    private void excluir(ResumoDaImportacao selecionada) {
        if (selecionada == null) {
            ui.notificador().info("Selecione uma importação na tabela.");
            return;
        }
        Alert confirmacao = new Alert(Alert.AlertType.CONFIRMATION,
                "Excluir a importação de " + Formatos.dataHora(selecionada.importadaEm()) + "? Esta ação não pode ser desfeita.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmacao.setHeaderText("Excluir importação");
        confirmacao.initOwner(ui.janela());
        if (confirmacao.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                ui.local().historico().excluir(selecionada.id());
                ui.notificador().sucesso("Importação excluída.");
                ui.estadoMudou();
                aoExibir();
            } catch (RuntimeException e) {
                ui.notificador().erro(Mensagens.paraUsuario(e));
            }
        }
    }

    private VBox comparacao(List<ResumoDaImportacao> lista) {
        boolean permitido = ui.local().politica().permite(Funcionalidade.COMPARACAO_ENTRE_PERIODOS);
        VBox cartao = Ui.cartao(Ui.tituloDeCartao(permitido ? "Comparar períodos" : "Comparar períodos (Premium)"));
        if (!permitido) {
            cartao.getChildren().addAll(Ui.suave("Compare duas exportações para ver contas novas e contas ausentes entre os períodos."),
                    Ui.primario("Conhecer o Premium", () -> ui.roteador().ir(Destino.PREMIUM)));
            return cartao;
        }
        if (lista.size() < 2) {
            cartao.getChildren().add(Ui.suave("Importe ao menos duas exportações para comparar períodos."));
            return cartao;
        }
        ComboBox<ResumoDaImportacao> a = seletor(lista, 1);
        ComboBox<ResumoDaImportacao> b = seletor(lista, 0);
        ComboBox<TipoDeRelacionamento> tipo = new ComboBox<>();
        tipo.getItems().setAll(TipoDeRelacionamento.SEGUIDOR, TipoDeRelacionamento.SEGUINDO);
        tipo.setValue(TipoDeRelacionamento.SEGUIDOR);
        tipo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(TipoDeRelacionamento t) {
                return t == null ? "" : t.rotulo();
            }

            @Override
            public TipoDeRelacionamento fromString(String s) {
                return null;
            }
        });
        VBox resultado = new VBox(12);
        HBox linha = new HBox(10, new Label("Anterior:"), a, new Label("Mais recente:"), b, new Label("Lista:"), tipo,
                Ui.primario("Comparar", () -> comparar(a.getValue(), b.getValue(), tipo.getValue(), resultado)));
        linha.setAlignment(Pos.CENTER_LEFT);
        cartao.getChildren().addAll(linha, resultado);
        return cartao;
    }

    private static ComboBox<ResumoDaImportacao> seletor(List<ResumoDaImportacao> lista, int indice) {
        ComboBox<ResumoDaImportacao> c = new ComboBox<>();
        c.getItems().setAll(lista);
        c.setValue(lista.get(Math.min(indice, lista.size() - 1)));
        c.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ResumoDaImportacao r) {
                return r == null ? "" : Formatos.dataHora(r.importadaEm());
            }

            @Override
            public ResumoDaImportacao fromString(String s) {
                return null;
            }
        });
        return c;
    }

    private void comparar(ResumoDaImportacao a, ResumoDaImportacao b, TipoDeRelacionamento tipo, VBox saida) {
        saida.getChildren().clear();
        if (a == null || b == null || a.id().equals(b.id())) {
            saida.getChildren().add(Ui.caixa(Ui.Tipo.INFO, "Escolha duas importações diferentes."));
            return;
        }
        try {
            ComparacaoDeImportacoes c = ui.local().historico().comparar(a.id(), b.id(), tipo);
            if (!c.confiavel()) {
                saida.getChildren().add(Ui.caixa(Ui.Tipo.AVISO,
                        "Uma das listas está incompleta ou ausente em uma das importações. Interprete as diferenças com cautela."));
            }
            TabPane abas = new TabPane(
                    aba("Novas (" + c.novas().size() + ")", c.novas()),
                    aba("Ausentes (" + c.ausentes().size() + ")", c.ausentes()),
                    aba("Mantidas (" + c.mantidas().size() + ")", c.mantidas()));
            abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            abas.setPrefHeight(280);
            saida.getChildren().addAll(abas, Ui.caixa(Ui.Tipo.AVISO, ComparacaoDeImportacoes.LIMITACAO));
        } catch (RuntimeException e) {
            saida.getChildren().add(Ui.caixa(Ui.Tipo.ERRO, Mensagens.paraUsuario(e)));
        }
    }

    private static Tab aba(String titulo, Set<NomeDeUsuario> contas) {
        ListView<String> lista = new ListView<>();
        contas.forEach(c -> lista.getItems().add(c.valor()));
        lista.setPlaceholder(new Label("Nenhuma conta."));
        return new Tab(titulo, lista);
    }
}
