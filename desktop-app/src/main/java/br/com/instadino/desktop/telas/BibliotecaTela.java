package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.desktop.util.Tarefas;
import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.servicos.Pagina;
import br.com.instadino.servicos.ServicoDeConteudo;
import br.com.instadino.servicos.ServicoDeConteudo.Item;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Catálogo local de publicações, reels e stories (Premium). Só metadados; as mídias ficam no ZIP original. */
final class BibliotecaTela extends TelaBase {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private final VBox area = new VBox(16);

    BibliotecaTela(ContextoDaInterface ui) {
        super(ui, "Biblioteca de conteúdo", "Suas publicações, reels e stories: legenda, data e arquivos de mídia.");
        corpo(area);
    }

    @Override
    public void aoExibir() {
        area.getChildren().clear();
        if (!ui.local().politica().permite(Funcionalidade.BIBLIOTECA_DE_CONTEUDO)) {
            area.getChildren().add(Ui.cartao(Ui.tituloDeCartao("Biblioteca de conteúdo (Premium)"),
                    Ui.suave("Catálogo local com busca por legenda, filtros por tipo e período e estatísticas de frequência de publicação."),
                    Ui.primario("Conhecer o Premium", () -> ui.roteador().ir(Destino.PREMIUM))));
            return;
        }
        if (!ui.local().conteudo().temDados()) {
            area.getChildren().add(Ui.cartao(Ui.estadoVazio("Sem conteúdo catalogado",
                    "A importação mais recente não tem publicações, reels ou stories. Importe uma exportação em JSON que inclua conteúdo. "
                            + "Importações feitas no plano Free não guardam conteúdo: importe de novo depois de assinar.",
                    Ui.primario("Importar exportação", () -> ui.roteador().ir(Destino.IMPORTACAO)))));
            return;
        }
        area.getChildren().add(Ui.caixa(Ui.Tipo.INFO, ServicoDeConteudo.LIMITACAO));
        area.getChildren().add(estatisticas());
        area.getChildren().add(catalogo());
    }

    private VBox estatisticas() {
        var totais = ui.local().conteudo().totais();
        FlowPane cartoes = new FlowPane(16, 16);
        for (CategoriaDeDados c : ServicoDeConteudo.CATEGORIAS) {
            Integer n = totais.porCategoria().get(c);
            if (n == null) {
                continue;
            }
            String detalhe = ui.local().conteudo().frequencia(c).map(f -> String.format(Locale.forLanguageTag("pt-BR"),
                    "média de %.1f por mês • mais ativo: %s (%d)", f.mediaPorMesNoPeriodo(), MES.format(f.mesMaisAtivo()), f.publicacoesNoMesMaisAtivo())).orElse("sem datas");
            VBox m = Ui.metrica(c.rotulo(), new Label(Formatos.inteiro(n)), detalhe);
            m.setPrefWidth(280);
            cartoes.getChildren().add(m);
        }
        for (var m : totais.midias()) {
            VBox v = Ui.metrica("Mídias: " + (m.tipo().equals("FOTO") ? "fotos" : m.tipo().equals("VIDEO") ? "vídeos" : "outras"),
                    new Label(Formatos.inteiro(m.quantidade())), m.bytes() > 0 ? Formatos.bytes(m.bytes()) + " no ZIP" : "tamanho desconhecido");
            v.setPrefWidth(220);
            cartoes.getChildren().add(v);
        }
        ComboBox<CategoriaDeDados> categoria = new ComboBox<>();
        categoria.getItems().addAll(totais.porCategoria().keySet());
        categoria.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(CategoriaDeDados c) {
                return c == null ? "" : c.rotulo();
            }

            @Override
            public CategoriaDeDados fromString(String s) {
                return null;
            }
        });
        VBox grafico = new VBox();
        Runnable desenhar = () -> {
            if (categoria.getValue() == null) {
                return;
            }
            Map<YearMonth, Integer> porMes = ui.local().conteudo().porMes(categoria.getValue());
            if (porMes.isEmpty()) {
                grafico.getChildren().setAll(Ui.suave("Esta categoria não tem itens com data."));
                return;
            }
            BarChart<String, Number> g = new BarChart<>(new CategoryAxis(), new NumberAxis());
            g.setLegendVisible(false);
            g.setAnimated(false);
            g.setPrefHeight(260);
            g.setAccessibleText("Gráfico de barras: " + categoria.getValue().rotulo() + " por mês");
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            porMes.forEach((mes, n) -> s.getData().add(new XYChart.Data<>(MES.format(mes), n)));
            g.getData().add(s);
            grafico.getChildren().setAll(g);
        };
        categoria.setOnAction(e -> desenhar.run());
        if (!categoria.getItems().isEmpty()) {
            categoria.setValue(categoria.getItems().get(0));
            desenhar.run();
        }
        return new VBox(16, cartoes, Ui.cartao(Ui.tituloDeCartao("Frequência de publicação por mês"), new HBox(8, new Label("Categoria:"), categoria), grafico));
    }

    private VBox catalogo() {
        TextField legenda = new TextField();
        legenda.setPromptText("Buscar na legenda");
        legenda.setAccessibleText("Buscar na legenda");
        ComboBox<CategoriaDeDados> categoria = new ComboBox<>();
        categoria.getItems().add(null);
        categoria.getItems().addAll(ServicoDeConteudo.CATEGORIAS);
        categoria.setValue(null);
        categoria.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(CategoriaDeDados c) {
                return c == null ? "Todos os tipos de conteúdo" : c.rotulo();
            }

            @Override
            public CategoriaDeDados fromString(String s) {
                return null;
            }
        });
        ComboBox<String> midia = new ComboBox<>();
        midia.getItems().addAll(null, "FOTO", "VIDEO", "OUTRO");
        midia.setValue(null);
        midia.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String s) {
                return s == null ? "Qualquer mídia" : s.equals("FOTO") ? "Com foto" : s.equals("VIDEO") ? "Com vídeo" : "Outras mídias";
            }

            @Override
            public String fromString(String s) {
                return null;
            }
        });
        TableView<Item> tabela = new TableView<>();
        tabela.getColumns().add(coluna("Data", i -> i.data() == null ? "—" : Formatos.dataHora(i.data()), 150));
        tabela.getColumns().add(coluna("Tipo", i -> i.categoria().rotulo(), 130));
        tabela.getColumns().add(coluna("Legenda", i -> i.legenda() == null ? "(sem legenda)" : i.legenda().replace('\n', ' '), 380));
        tabela.getColumns().add(coluna("Mídias", i -> resumoDeMidias(i), 190));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setPrefHeight(380);
        tabela.setAccessibleText("Catálogo de conteúdo");
        VBox detalhe = new VBox(4);
        tabela.getSelectionModel().selectedItemProperty().addListener((o, a, item) -> {
            detalhe.getChildren().clear();
            if (item != null) {
                detalhe.getChildren().add(Ui.tituloDeCartao("Arquivos de mídia deste item"));
                if (item.legenda() != null) {
                    Label l = Ui.suave(item.legenda());
                    l.setWrapText(true);
                    detalhe.getChildren().add(l);
                }
                item.midias().forEach(m -> detalhe.getChildren().add(Ui.pequeno(m.caminho() + "  •  " + m.tipo().toLowerCase(Locale.ROOT)
                        + (m.tamanhoEmBytes() == null ? "  •  não encontrado no ZIP" : "  •  " + Formatos.bytes(m.tamanhoEmBytes())))));
                if (item.midias().isEmpty()) {
                    detalhe.getChildren().add(Ui.pequeno("Sem arquivos de mídia registrados."));
                }
            }
        });
        Label info = new Label();
        info.getStyleClass().add("texto-suave");
        int[] pagina = {0};
        Button anterior = Ui.secundario("‹ Anterior", () -> { });
        Button proxima = Ui.secundario("Próxima ›", () -> { });
        Runnable[] carregar = new Runnable[1];
        carregar[0] = () -> {
            String t = legenda.getText();
            CategoriaDeDados c = categoria.getValue();
            String m = midia.getValue();
            int p = pagina[0];
            Tarefas.executar(prog -> ui.local().conteudo().buscar(c, t, m, null, null, p, 50), (Pagina<Item> r) -> {
                tabela.getItems().setAll(r.itens());
                info.setText(Formatos.inteiro(r.total()) + " itens  •  página " + (r.pagina() + 1) + " de " + Math.max(1, r.totalDePaginas()));
                anterior.setDisable(r.pagina() == 0);
                proxima.setDisable(r.pagina() + 1 >= r.totalDePaginas());
            }, erro -> ui.notificador().erro(Mensagens.paraUsuario(erro)));
        };
        anterior.setOnAction(e -> {
            pagina[0] = Math.max(0, pagina[0] - 1);
            carregar[0].run();
        });
        proxima.setOnAction(e -> {
            pagina[0]++;
            carregar[0].run();
        });
        Button buscar = Ui.primario("Buscar", () -> {
            pagina[0] = 0;
            carregar[0].run();
        });
        legenda.setOnAction(e -> buscar.fire());
        categoria.setOnAction(e -> buscar.fire());
        midia.setOnAction(e -> buscar.fire());
        carregar[0].run();
        return Ui.cartao(Ui.tituloDeCartao("Catálogo"), new HBox(8, legenda, categoria, midia, buscar), tabela,
                new HBox(12, anterior, proxima, info), detalhe);
    }

    private static String resumoDeMidias(Item i) {
        if (i.midias().isEmpty()) {
            return "—";
        }
        long fotos = i.midias().stream().filter(m -> m.tipo().equals("FOTO")).count();
        long videos = i.midias().stream().filter(m -> m.tipo().equals("VIDEO")).count();
        long bytes = i.midias().stream().mapToLong(m -> m.tamanhoEmBytes() == null ? 0 : m.tamanhoEmBytes()).sum();
        return (fotos > 0 ? fotos + " foto(s) " : "") + (videos > 0 ? videos + " vídeo(s) " : "") + (bytes > 0 ? "• " + Formatos.bytes(bytes) : "");
    }

    private static <T> TableColumn<T, String> coluna(String titulo, java.util.function.Function<T, String> valor, double largura) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        c.setPrefWidth(largura);
        return c;
    }
}
