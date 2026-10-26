package br.com.instadino.desktop.telas;

import br.com.instadino.analise.social.AnaliseDeSeguidores;
import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.util.Formatos;
import br.com.instadino.desktop.util.Tarefas;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes.Anotacao;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes.Etiqueta;
import br.com.instadino.servicos.ConsultaDeRelacionamentos;
import br.com.instadino.servicos.ConsultaDeRelacionamentos.Ordenacao;
import br.com.instadino.servicos.ContaListada;
import br.com.instadino.servicos.Pagina;
import br.com.instadino.servicos.VisaoDeRelacionamento;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/** Listas de relacionamentos com busca, ordenação, filtros, paginação e exportação CSV. */
final class RelacionamentosTela extends TelaBase {

    private static final int TAMANHO_DA_PAGINA = 100;

    private final ToggleGroup grupo = new ToggleGroup();
    private final Map<VisaoDeRelacionamento, ToggleButton> abas = new EnumMap<>(VisaoDeRelacionamento.class);
    private final FlowPane barraDeAbas = new FlowPane(8, 8);
    private final TextField busca = new TextField();
    private final ComboBox<Ordenacao> ordem = new ComboBox<>();
    private final TableView<ContaListada> tabela = new TableView<>();
    private final Label contagem = new Label();
    private final Label paginaInfo = new Label();
    private final Button anterior = new Button("‹ Anterior");
    private final Button proxima = new Button("Próxima ›");
    private final VBox avisoLimitacao = new VBox();
    private final VBox painelAvancado = new VBox(8);
    private final DatePicker desde = new DatePicker();
    private final DatePicker ate = new DatePicker();
    private final CheckBox somenteComData = new CheckBox("Somente contas com data registrada");
    private final CheckBox somenteFavoritas = new CheckBox("Somente favoritas");
    private final ComboBox<Etiqueta> filtroDeEtiqueta = new ComboBox<>();
    private Map<String, Anotacao> anotacoesVisiveis = new HashMap<>();
    private final PauseTransition atrasoDaBusca = new PauseTransition(Duration.millis(250));
    private VisaoDeRelacionamento visao = VisaoDeRelacionamento.SEGUIDORES;
    private int pagina;
    private boolean carregandoEstado;

    RelacionamentosTela(ContextoDaInterface ui) {
        super(ui, "Relacionamentos", "Seguidores, contas seguidas e comparações da sua última exportação.");
        configurarControles();
        corpo(barraDeAbas, barraDeFerramentas(), painelAvancado, avisoLimitacao, tabela, rodape());
        VBox.setVgrow(tabela, Priority.ALWAYS);
    }

    private void configurarControles() {
        for (VisaoDeRelacionamento v : VisaoDeRelacionamento.values()) {
            ToggleButton b = new ToggleButton(v.rotulo());
            b.getStyleClass().add("botao-aba");
            b.setToggleGroup(grupo);
            b.setOnAction(e -> {
                if (!carregandoEstado) {
                    visao = v;
                    pagina = 0;
                    carregar();
                }
            });
            abas.put(v, b);
        }
        grupo.selectedToggleProperty().addListener((o, antigo, novo) -> {
            if (novo == null && antigo != null) {
                antigo.setSelected(true);
            }
        });
        busca.setPromptText("Buscar por nome de usuário");
        busca.setAccessibleText("Buscar por nome de usuário");
        busca.textProperty().addListener((o, a, n) -> {
            if (!carregandoEstado) {
                atrasoDaBusca.playFromStart();
            }
        });
        atrasoDaBusca.setOnFinished(e -> {
            pagina = 0;
            carregar();
        });
        ordem.getItems().setAll(Ordenacao.values());
        ordem.setValue(Ordenacao.ALFABETICA);
        ordem.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Ordenacao o) {
                return o == null ? "" : switch (o) {
                    case ALFABETICA -> "Nome (A–Z)";
                    case ALFABETICA_INVERSA -> "Nome (Z–A)";
                    case MAIS_RECENTES -> "Mais recentes";
                    case MAIS_ANTIGOS -> "Mais antigos";
                };
            }

            @Override
            public Ordenacao fromString(String s) {
                return null;
            }
        });
        ordem.setOnAction(e -> {
            if (!carregandoEstado) {
                pagina = 0;
                carregar();
            }
        });
        ordem.setAccessibleText("Ordenação");

        TableColumn<ContaListada, ContaListada> colFavorito = new TableColumn<>("★");
        colFavorito.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        colFavorito.setSortable(false);
        colFavorito.setPrefWidth(52);
        colFavorito.setMinWidth(52);
        colFavorito.setMaxWidth(52);
        colFavorito.setCellFactory(coluna -> new TableCell<>() {
            private final Button estrela = new Button();

            {
                estrela.getStyleClass().add("botao-estrela");
            }

            @Override
            protected void updateItem(ContaListada conta, boolean vazio) {
                super.updateItem(conta, vazio);
                if (vazio || conta == null) {
                    setGraphic(null);
                    return;
                }
                boolean marcada = anotacoesVisiveis.containsKey(conta.usuario()) && anotacoesVisiveis.get(conta.usuario()).favorito();
                estrela.setText(marcada ? "★" : "☆");
                estrela.getStyleClass().remove("estrela-marcada");
                if (marcada) {
                    estrela.getStyleClass().add("estrela-marcada");
                }
                estrela.setAccessibleText((marcada ? "Remover @" : "Marcar como favorita @") + conta.usuario());
                estrela.setOnAction(e -> alternarFavorito(conta.usuario(), !marcada));
                setGraphic(estrela);
            }
        });
        TableColumn<ContaListada, String> colUsuario = new TableColumn<>("Usuário");
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().usuario()));
        colUsuario.setPrefWidth(260);
        TableColumn<ContaListada, String> colData = new TableColumn<>("Registrado em");
        colData.setCellValueFactory(c -> new SimpleStringProperty(Formatos.data(c.getValue().registradaEm())));
        colData.setPrefWidth(160);
        TableColumn<ContaListada, String> colAnotacoes = new TableColumn<>("Etiquetas e nota");
        colAnotacoes.setCellValueFactory(c -> new SimpleStringProperty(resumoDasAnotacoes(c.getValue().usuario())));
        colAnotacoes.setSortable(false);
        colAnotacoes.setPrefWidth(220);
        TableColumn<ContaListada, ContaListada> colAcao = new TableColumn<>("");
        colAcao.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        colAcao.setSortable(false);
        colAcao.setCellFactory(coluna -> new TableCell<>() {
            private final Button abrir = new Button("Abrir perfil");
            private final Button anotar = new Button("Anotações…");
            private final HBox acoes = new HBox(6, anotar, abrir);

            {
                abrir.getStyleClass().add("botao-link");
                anotar.getStyleClass().add("botao-link");
                acoes.setAlignment(Pos.CENTER_LEFT);
                setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(ContaListada conta, boolean vazio) {
                super.updateItem(conta, vazio);
                if (vazio || conta == null) {
                    setGraphic(null);
                } else {
                    abrir.setOnAction(e -> ui.abrirPerfil(conta.urlDoPerfil()));
                    abrir.setAccessibleText("Abrir o perfil de " + conta.usuario() + " no navegador");
                    anotar.setOnAction(e -> editarAnotacoes(conta.usuario()));
                    anotar.setAccessibleText("Favorito, etiquetas e nota de " + conta.usuario());
                    setGraphic(acoes);
                }
            }
        });
        colAcao.setPrefWidth(230);
        tabela.getColumns().add(colFavorito);
        tabela.getColumns().add(colUsuario);
        tabela.getColumns().add(colData);
        tabela.getColumns().add(colAnotacoes);
        tabela.getColumns().add(colAcao);
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setPlaceholder(new Label("Nenhuma conta para mostrar."));
        tabela.setMinHeight(380);
        tabela.setAccessibleText("Lista de contas");
        tabela.setRowFactory(t -> {
            var linha = new javafx.scene.control.TableRow<ContaListada>();
            linha.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !linha.isEmpty()) {
                    ui.abrirPerfil(linha.getItem().urlDoPerfil());
                }
            });
            return linha;
        });
        anterior.setOnAction(e -> {
            pagina = Math.max(0, pagina - 1);
            carregar();
        });
        proxima.setOnAction(e -> {
            pagina++;
            carregar();
        });
        construirPainelAvancado();
    }

    private void construirPainelAvancado() {
        boolean premium = ui.local().politica().permite(Funcionalidade.FILTROS_COMPOSTOS);
        Label titulo = Ui.tituloDeCartao(premium ? "Filtros avançados" : "Filtros avançados (Premium)");
        desde.setPromptText("De");
        ate.setPromptText("Até");
        Button aplicar = Ui.secundario("Aplicar filtros", () -> {
            pagina = 0;
            carregar();
        });
        Button limpar = Ui.link("Limpar", () -> {
            desde.setValue(null);
            ate.setValue(null);
            somenteComData.setSelected(false);
            somenteFavoritas.setSelected(false);
            filtroDeEtiqueta.setValue(null);
            pagina = 0;
            carregar();
        });
        desde.setPrefWidth(150);
        ate.setPrefWidth(150);
        boolean anotacoesLiberadas = ui.local().politica().permite(Funcionalidade.FAVORITOS) && ui.local().politica().permite(Funcionalidade.ETIQUETAS);
        somenteFavoritas.setDisable(!anotacoesLiberadas);
        filtroDeEtiqueta.setPromptText("Etiqueta");
        filtroDeEtiqueta.setDisable(!anotacoesLiberadas);
        filtroDeEtiqueta.setAccessibleText("Filtrar por etiqueta");
        filtroDeEtiqueta.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Etiqueta e) {
                return e == null ? "Todas as etiquetas" : e.nome();
            }

            @Override
            public Etiqueta fromString(String s) {
                return null;
            }
        });
        atualizarFiltroDeEtiqueta();
        FlowPane controles = new FlowPane(10, 8, new Label("Data de registro:"), desde, ate, somenteComData, somenteFavoritas, filtroDeEtiqueta, aplicar, limpar);
        controles.setAlignment(Pos.CENTER_LEFT);
        painelAvancado.getStyleClass().add("cartao");
        painelAvancado.getChildren().setAll(titulo, controles);
        if (!premium) {
            controles.setDisable(true);
            painelAvancado.getChildren().add(new HBox(8, Ui.pequeno("Combine filtros de período, favoritas e etiquetas com o plano Premium."),
                    Ui.link("Conhecer o Premium", () -> ui.roteador().ir(Destino.PREMIUM))));
        }
    }

    private HBox barraDeFerramentas() {
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox.setHgrow(busca, Priority.ALWAYS);
        busca.setMaxWidth(380);
        Button exportar = Ui.secundario("Exportar CSV", this::exportar);
        HBox h = new HBox(10, busca, new Label("Ordenar:"), ordem, espaco, exportar);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private HBox rodape() {
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        contagem.getStyleClass().add("texto-suave");
        paginaInfo.getStyleClass().add("texto-suave");
        HBox h = new HBox(12, contagem, espaco, anterior, paginaInfo, proxima);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(4, 0, 0, 0));
        return h;
    }

    /** Abre a tela já em uma visão e com um termo de busca. */
    void abrir(VisaoDeRelacionamento nova, String termo) {
        carregandoEstado = true;
        visao = nova;
        busca.setText(termo == null ? "" : termo);
        pagina = 0;
        carregandoEstado = false;
    }

    @Override
    public void aoExibir() {
        atualizarAbas();
        atualizarFiltroDeEtiqueta();
        carregar();
    }

    private void atualizarAbas() {
        barraDeAbas.getChildren().clear();
        for (VisaoDeRelacionamento v : VisaoDeRelacionamento.values()) {
            boolean opcional = v == VisaoDeRelacionamento.PENDENTES || v == VisaoDeRelacionamento.BLOQUEADOS
                    || v == VisaoDeRelacionamento.RESTRITOS || v == VisaoDeRelacionamento.FAVORITOS;
            if (opcional && v != visao) {
                int total = ui.local().relacionamentos().consultar(new ConsultaDeRelacionamentos(v, "", Ordenacao.ALFABETICA, 0, 1, null, null, false)).total();
                if (total == 0) {
                    continue;
                }
            }
            barraDeAbas.getChildren().add(abas.get(v));
        }
        carregandoEstado = true;
        abas.get(visao).setSelected(true);
        carregandoEstado = false;
    }

    private ConsultaDeRelacionamentos consulta() {
        var inicio = desde.getValue() == null ? null : desde.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant();
        var fim = ate.getValue() == null ? null : ate.getValue().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusSeconds(1);
        Long etiqueta = filtroDeEtiqueta.getValue() == null ? null : filtroDeEtiqueta.getValue().id();
        return new ConsultaDeRelacionamentos(visao, busca.getText(), ordem.getValue(), pagina, TAMANHO_DA_PAGINA, inicio, fim,
                somenteComData.isSelected(), somenteFavoritas.isSelected(), etiqueta);
    }

    private record Carga(Pagina<ContaListada> pagina, Map<String, Anotacao> anotacoes) {
    }

    private void atualizarFiltroDeEtiqueta() {
        Etiqueta escolhida = filtroDeEtiqueta.getValue();
        List<Etiqueta> todas = ui.local().anotacoes().etiquetas();
        filtroDeEtiqueta.getItems().setAll(todas);
        filtroDeEtiqueta.setValue(escolhida != null && todas.stream().anyMatch(e -> e.id() == escolhida.id()) ? escolhida : null);
    }

    private String resumoDasAnotacoes(String usuario) {
        Anotacao a = anotacoesVisiveis.get(usuario);
        if (a == null) {
            return "";
        }
        String etiquetas = a.etiquetas().stream().map(Etiqueta::nome).collect(java.util.stream.Collectors.joining(", "));
        String nota = a.nota() == null || a.nota().isEmpty() ? "" : "nota";
        return etiquetas.isEmpty() ? nota : nota.isEmpty() ? etiquetas : etiquetas + "  •  " + nota;
    }

    private void alternarFavorito(String usuario, boolean favorito) {
        try {
            ui.local().anotacoes().favoritar(usuario, favorito);
        } catch (RuntimeException e) {
            ui.notificador().erro(Mensagens.paraUsuario(e));
            if (e instanceof br.com.instadino.nucleo.acesso.AcessoNegadoException) {
                ui.roteador().ir(Destino.PREMIUM);
            }
            return;
        }
        carregar();
    }

    private void editarAnotacoes(String usuario) {
        AnotacoesDialogo editor = new AnotacoesDialogo(ui, usuario);
        editor.dialogo().showAndWait();
        atualizarFiltroDeEtiqueta();
        carregar();
    }

    private void carregar() {
        ConsultaDeRelacionamentos c = consulta();
        Tarefas.executar(p -> new Carga(ui.local().relacionamentos().consultar(c), ui.local().anotacoes().doPerfilAtivo()), carga -> {
            anotacoesVisiveis = carga.anotacoes();
            mostrar(carga.pagina());
        }, erro -> {
            ui.notificador().erro(Mensagens.paraUsuario(erro));
            tabela.getItems().clear();
        });
    }

    private void mostrar(Pagina<ContaListada> resultado) {
        if (resultado.pagina() >= resultado.totalDePaginas() && resultado.pagina() > 0) {
            pagina = Math.max(0, resultado.totalDePaginas() - 1);
            carregar();
            return;
        }
        tabela.getItems().setAll(resultado.itens());
        tabela.refresh();
        tabela.scrollTo(0);
        contagem.setText(Formatos.inteiro(resultado.total()) + (resultado.total() == 1 ? " conta" : " contas"));
        int total = Math.max(resultado.totalDePaginas(), 1);
        paginaInfo.setText("Página " + Formatos.inteiro(resultado.pagina() + 1L) + " de " + Formatos.inteiro(total));
        anterior.setDisable(resultado.pagina() == 0);
        proxima.setDisable(resultado.pagina() + 1 >= resultado.totalDePaginas());
        avisoLimitacao.getChildren().clear();
        if (visao == VisaoDeRelacionamento.NAO_SEGUEM_DE_VOLTA || visao == VisaoDeRelacionamento.VOCE_NAO_SEGUE) {
            avisoLimitacao.getChildren().add(Ui.caixa(Ui.Tipo.AVISO, AnaliseDeSeguidores.LIMITACAO));
        }
        if (resultado.total() == 0 && ui.local().relacionamentos().painel().isEmpty()) {
            tabela.setPlaceholder(new Label("Importe uma exportação para ver suas listas."));
        } else {
            tabela.setPlaceholder(new Label("Nenhuma conta para mostrar."));
        }
    }

    private void exportar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar lista em CSV");
        chooser.setInitialFileName("instadino-" + visao.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos CSV", "*.csv"));
        File destino = chooser.showSaveDialog(ui.janela());
        if (destino == null) {
            return;
        }
        VisaoDeRelacionamento escolhida = visao;
        Tarefas.executar(p -> {
            try (OutputStream saida = Files.newOutputStream(destino.toPath())) {
                ui.local().exportacao().exportarCsv(escolhida, saida);
            }
            return destino.getName();
        }, nome -> ui.notificador().sucesso("Arquivo " + nome + " salvo."), erro -> ui.notificador().erro(Mensagens.paraUsuario(erro)));
    }
}
