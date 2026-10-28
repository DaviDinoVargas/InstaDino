package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.MapaDeCalor;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.desktop.util.Tarefas;
import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.servicos.Pagina;
import br.com.instadino.servicos.ServicoDeAtividade;
import br.com.instadino.servicos.ServicoDeAtividade.ItemDeRanking;
import br.com.instadino.servicos.ServicoDeAtividade.Registro;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Atividade registrada na exportação (Premium): resumo, calendário, ranking, pesquisa e cruzamentos. */
final class AtividadeTela extends TelaBase {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private final VBox area = new VBox(16);

    AtividadeTela(ContextoDaInterface ui) {
        super(ui, "Atividade", "O que a sua exportação registra de curtidas, comentários, salvos, pesquisas e visualizações.");
        corpo(area);
    }

    @Override
    public void aoExibir() {
        area.getChildren().clear();
        if (!ui.local().politica().permite(Funcionalidade.ANALISE_DE_ATIVIDADE)) {
            area.getChildren().add(Ui.cartao(Ui.tituloDeCartao("Análise de atividade (Premium)"),
                    Ui.suave("Veja o calendário de atividade, quem você mais curte e comenta, pesquisas e cruzamentos com suas listas. "
                            + "Tudo calculado localmente, só a partir dos registros da exportação."),
                    Ui.primario("Conhecer o Premium", () -> ui.roteador().ir(Destino.PREMIUM))));
            return;
        }
        boolean comAtividade = ui.local().atividade().temDados();
        boolean comInteresses = ui.local().atividade().temInteresses();
        if (!comAtividade && !comInteresses) {
            area.getChildren().add(Ui.cartao(Ui.estadoVazio("Sem dados de atividade",
                    "A importação mais recente não tem registros de atividade. Importe uma exportação em JSON que inclua atividade "
                            + "(curtidas, comentários etc.). Importações feitas no plano Free não guardam atividade: importe de novo depois de assinar.",
                    Ui.primario("Importar exportação", () -> ui.roteador().ir(Destino.IMPORTACAO)))));
            return;
        }
        area.getChildren().add(Ui.caixa(Ui.Tipo.INFO, ServicoDeAtividade.LIMITACAO));
        TabPane abas = new TabPane();
        if (comAtividade) {
            abas.getTabs().addAll(new Tab("Resumo", resumo()), new Tab("Calendário", calendario()), new Tab("Ranking", ranking()),
                    new Tab("Pesquisar", pesquisa()), new Tab("Cruzamentos", cruzamentos()));
        }
        if (comInteresses) {
            abas.getTabs().add(new Tab("Anúncios e interesses", interesses()));
        }
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        abas.setMinHeight(560);
        area.getChildren().add(abas);
    }

    private static String rotulo(CategoriaDeDados c) {
        return c.rotulo();
    }

    private static ComboBox<CategoriaDeDados> seletorDeCategoria(boolean comTodas) {
        ComboBox<CategoriaDeDados> c = new ComboBox<>();
        if (comTodas) {
            c.getItems().add(null);
        }
        c.getItems().addAll(ServicoDeAtividade.CATEGORIAS);
        c.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(CategoriaDeDados x) {
                return x == null ? "Todas as categorias" : rotulo(x);
            }

            @Override
            public CategoriaDeDados fromString(String s) {
                return null;
            }
        });
        c.setValue(comTodas ? null : ServicoDeAtividade.CATEGORIAS.get(0));
        return c;
    }

    // ---- resumo ----

    private VBox resumo() {
        FlowPane cartoes = new FlowPane(16, 16);
        for (var r : ui.local().atividade().resumo()) {
            String periodo = r.de() == null ? "sem datas" : Formatos.data(r.de()) + " a " + Formatos.data(r.ate());
            Label total = new Label(Formatos.inteiro(r.total()));
            VBox m = Ui.metrica(rotulo(r.categoria()), total, periodo);
            m.setPrefWidth(250);
            cartoes.getChildren().add(m);
        }
        ComboBox<CategoriaDeDados> categoria = seletorDeCategoria(false);
        VBox grafico = new VBox();
        Runnable desenhar = () -> {
            var porMes = ui.local().atividade().porMes(categoria.getValue());
            grafico.getChildren().setAll(porMes.isEmpty() ? Ui.suave("Esta categoria não tem registros com data.") : graficoPorMes(porMes, categoria.getValue()));
        };
        categoria.setOnAction(e -> desenhar.run());
        desenhar.run();
        return new VBox(16, cartoes, Ui.cartao(Ui.tituloDeCartao("Registros por mês"), new HBox(8, new Label("Categoria:"), categoria), grafico));
    }

    private BarChart<String, Number> graficoPorMes(Map<YearMonth, Integer> porMes, CategoriaDeDados categoria) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        BarChart<String, Number> g = new BarChart<>(x, y);
        g.setLegendVisible(false);
        g.setAnimated(false);
        g.setPrefHeight(300);
        g.setAccessibleText("Gráfico de barras com os registros de " + rotulo(categoria) + " por mês");
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        porMes.forEach((mes, n) -> s.getData().add(new XYChart.Data<>(MES.format(mes), n)));
        g.getData().add(s);
        return g;
    }

    // ---- calendário ----

    private VBox calendario() {
        ComboBox<CategoriaDeDados> categoria = seletorDeCategoria(true);
        VBox conteudo = new VBox(12);
        Runnable desenhar = () -> {
            List<CategoriaDeDados> alvo = categoria.getValue() == null ? ServicoDeAtividade.CATEGORIAS : List.of(categoria.getValue());
            int[][] grade = ui.local().atividade().mapaDeCalor(alvo);
            VBox dias = new VBox(4, Ui.tituloDeCartao("Dias com mais registros"));
            java.util.TreeMap<java.time.LocalDate, Integer> somado = new java.util.TreeMap<>();
            for (CategoriaDeDados c : alvo) {
                ui.local().atividade().porDia(c).forEach((d, n) -> somado.merge(d, n, Integer::sum));
            }
            somado.entrySet().stream().sorted(Map.Entry.<java.time.LocalDate, Integer>comparingByValue().reversed()).limit(8)
                    .forEach(e -> dias.getChildren().add(Ui.suave(e.getKey().format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")))
                            + " — " + Formatos.inteiro(e.getValue()) + " registros")));
            conteudo.getChildren().setAll(Ui.cartao(Ui.tituloDeCartao("Horários e dias da semana (horário do seu computador)"), MapaDeCalor.criar(grade)),
                    Ui.cartao(dias.getChildren().toArray(new javafx.scene.Node[0])));
        };
        categoria.setOnAction(e -> desenhar.run());
        desenhar.run();
        return new VBox(12, new HBox(8, new Label("Categoria:"), categoria), conteudo);
    }

    // ---- anúncios e interesses ----

    private VBox interesses() {
        VBox conteudo = new VBox(12, Ui.pequeno(ServicoDeAtividade.LIMITACAO_DE_INTERESSES));
        for (var grupo : ui.local().atividade().interesses(200)) {
            TableView<ServicoDeAtividade.ItemDeInteresse> tabela = new TableView<>();
            tabela.getColumns().add(coluna("Item", i -> i.texto(), 420));
            tabela.getColumns().add(coluna("Vezes", i -> Formatos.inteiro(i.vezes()), 100));
            tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            tabela.getItems().setAll(grupo.itens());
            tabela.setPrefHeight(Math.min(360, 40 + Math.max(1, grupo.itens().size()) * 36));
            tabela.setAccessibleText("Itens do arquivo " + grupo.arquivo());
            conteudo.getChildren().add(Ui.cartao(Ui.tituloDeCartao(rotuloDeArquivo(grupo.arquivo()) + " (" + Formatos.inteiro(grupo.total()) + ")"),
                    Ui.pequeno("Arquivo de origem: " + grupo.arquivo()), tabela));
        }
        return conteudo;
    }

    /** Nomes conhecidos de arquivos de anúncios; os demais aparecem com o nome do arquivo. */
    private static String rotuloDeArquivo(String arquivo) {
        return switch (arquivo) {
            case "your_topics", "inferred_topics", "topics_your_topics" -> "Temas que o Instagram associa a você";
            case "advertisers_using_your_activity_or_information", "advertisers_you've_interacted_with" -> "Anunciantes ligados à sua atividade";
            case "ads_viewed", "ads_clicked" -> "Anúncios";
            default -> arquivo;
        };
    }

    // ---- ranking ----

    private VBox ranking() {
        ComboBox<CategoriaDeDados> categoria = seletorDeCategoria(false);
        TableView<ItemDeRanking> tabela = new TableView<>();
        tabela.getColumns().add(coluna("Conta", i -> i.conta(), 260));
        tabela.getColumns().add(coluna("Registros", i -> Formatos.inteiro(i.quantidade()), 110));
        tabela.getColumns().add(coluna("Você segue", i -> i.voceSegue() ? "sim" : "não", 110));
        tabela.getColumns().add(coluna("Segue você", i -> i.seguidor() ? "sim" : "não", 110));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setPrefHeight(420);
        tabela.setAccessibleText("Ranking de contas por quantidade de registros");
        tabela.setPlaceholder(new Label("Nenhuma conta identificada nesta categoria."));
        Runnable carregar = () -> tabela.getItems().setAll(ui.local().atividade().ranking(categoria.getValue(), 100));
        categoria.setOnAction(e -> carregar.run());
        carregar.run();
        tabela.setRowFactory(t -> {
            var linha = new javafx.scene.control.TableRow<ItemDeRanking>();
            linha.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !linha.isEmpty()) {
                    ui.abrirPerfil("https://www.instagram.com/" + linha.getItem().conta() + "/");
                }
            });
            return linha;
        });
        return new VBox(12, new HBox(8, new Label("Categoria:"), categoria),
                Ui.pequeno("Mostra quantas vezes a conta aparece nos seus registros. A situação \"você segue\" e \"segue você\" vem das listas da mesma "
                        + "exportação e pode estar incompleta. Clique duas vezes para abrir o perfil."), tabela);
    }

    // ---- pesquisa ----

    private VBox pesquisa() {
        TextField texto = new TextField();
        texto.setPromptText("Texto ou nome de usuário");
        texto.setAccessibleText("Texto para pesquisar nos registros de atividade");
        ComboBox<CategoriaDeDados> categoria = seletorDeCategoria(true);
        TableView<Registro> tabela = new TableView<>();
        tabela.getColumns().add(coluna("Data", r -> r.instante() == null ? "—" : Formatos.dataHora(r.instante()), 150));
        tabela.getColumns().add(coluna("Categoria", r -> rotulo(r.categoria()), 150));
        tabela.getColumns().add(coluna("Conta", r -> r.conta() == null ? "—" : r.conta(), 170));
        tabela.getColumns().add(coluna("Texto", r -> r.texto() == null ? "" : r.texto(), 300));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setPrefHeight(380);
        Label info = new Label();
        info.getStyleClass().add("texto-suave");
        int[] pagina = {0};
        Button anterior = Ui.secundario("‹ Anterior", () -> { });
        Button proxima = Ui.secundario("Próxima ›", () -> { });
        Runnable[] carregar = new Runnable[1];
        carregar[0] = () -> {
            String t = texto.getText();
            CategoriaDeDados c = categoria.getValue();
            int p = pagina[0];
            Tarefas.executar(prog -> ui.local().atividade().buscar(c, t, null, null, p, 100), (Pagina<Registro> r) -> {
                tabela.getItems().setAll(r.itens());
                info.setText(Formatos.inteiro(r.total()) + " registros  •  página " + (r.pagina() + 1) + " de " + Math.max(1, r.totalDePaginas()));
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
        Button buscar = Ui.primario("Pesquisar", () -> {
            pagina[0] = 0;
            carregar[0].run();
        });
        texto.setOnAction(e -> buscar.fire());
        categoria.setOnAction(e -> buscar.fire());
        carregar[0].run();
        return new VBox(12, new HBox(8, texto, categoria, buscar), tabela, new HBox(12, anterior, proxima, info));
    }

    // ---- cruzamentos ----

    private VBox cruzamentos() {
        var servico = ui.local().atividade();
        var c = servico.cruzarNaoReciprocos();
        VBox naoRec = Ui.cartao(Ui.tituloDeCartao("Contas que você segue e que não aparecem como seguidores (" + Formatos.inteiro(c.totalDeNaoReciprocos()) + ")"),
                Ui.pequeno("Separadas entre as que têm atividade SUA registrada (curtidas, comentários, salvos, stories, visualizações) e as que não têm. "
                        + "Não indica o motivo de nenhuma delas."),
                new Label("Com atividade sua: " + Formatos.inteiro(c.comInteracao().size()) + "  •  Sem nenhuma atividade sua: "
                        + Formatos.inteiro(c.semNenhumaInteracao().size())),
                listaDe("Com atividade (conta — registros)", c.comInteracao().stream().map(x -> x.conta() + " — " + Formatos.inteiro(x.interacoes())).toList()),
                listaDe("Sem nenhuma atividade sua registrada", c.semNenhumaInteracao()));
        var semAtividade = servico.seguidoresSemAtividadeSua();
        VBox seguidores = Ui.cartao(Ui.tituloDeCartao("Seguidores sem atividade sua registrada (" + Formatos.inteiro(semAtividade.size()) + ")"),
                Ui.pequeno("São contas que seguem você e com as quais VOCÊ não registrou nenhuma atividade. Isso não diz nada sobre o que elas fizeram com o seu perfil."),
                listaDe("Contas", semAtividade));
        var buscados = servico.perfisBuscados();
        TableView<ServicoDeAtividade.TermoBuscado> tabela = new TableView<>();
        tabela.getColumns().add(coluna("Termo pesquisado", t -> t.termo(), 260));
        tabela.getColumns().add(coluna("Vezes", t -> Formatos.inteiro(t.vezes()), 100));
        tabela.getColumns().add(coluna("Você segue", t -> t.voceSegue() ? "sim" : "não", 120));
        tabela.getItems().setAll(buscados);
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setPrefHeight(260);
        tabela.setPlaceholder(new Label("Nenhuma pesquisa por nome de usuário nos registros."));
        VBox pesquisados = Ui.cartao(Ui.tituloDeCartao("Perfis que você pesquisou"),
                Ui.pequeno("Termos pesquisados que têm formato de nome de usuário, com a quantidade de vezes e se você já segue. Não indica interesse nem intenção."), tabela);
        return new VBox(16, naoRec, seguidores, pesquisados);
    }

    private static VBox listaDe(String titulo, List<String> itens) {
        ListView<String> lista = new ListView<>();
        lista.getItems().setAll(itens.size() > 500 ? itens.subList(0, 500) : itens);
        lista.setPrefHeight(Math.min(220, 40 + itens.size() * 26));
        lista.setPlaceholder(new Label("Nenhuma conta."));
        lista.setAccessibleText(titulo);
        Label rotulo = Ui.pequeno(titulo + (itens.size() > 500 ? " (mostrando 500 de " + Formatos.inteiro(itens.size()) + ")" : ""));
        return new VBox(4, rotulo, lista);
    }

    private static <T> TableColumn<T, String> coluna(String titulo, java.util.function.Function<T, String> valor, double largura) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        c.setPrefWidth(largura);
        return c;
    }
}
