package br.com.instadino.desktop;

import br.com.instadino.desktop.componentes.Icones;
import br.com.instadino.desktop.componentes.Notificador;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.desktop.navegacao.Roteador;
import br.com.instadino.desktop.telas.Telas;
import br.com.instadino.licenciamento.GerenciadorDeLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.servicos.ContextoLocal;
import br.com.instadino.servicos.VisaoDeRelacionamento;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Window;

/** Janela principal: barra lateral, barra superior e área de conteúdo. */
public final class JanelaPrincipal {

    private static final String CSS_ESCURO = "/br/com/instadino/desktop/instadino.css";
    private static final String CSS_CLARO = "/br/com/instadino/desktop/instadino-claro.css";

    private final ContextoLocal local;
    private final GerenciadorDeLicenca licenca;
    private final BorderPane raiz = new BorderPane();
    private final StackPane area = new StackPane();
    private final Notificador notificador = new Notificador();
    private final Map<Destino, ToggleButton> botoes = new EnumMap<>(Destino.class);
    private final ToggleGroup grupo = new ToggleGroup();
    private final Label seloDoPlano = new Label();
    private final Button botaoDaConta = new Button("Entrar");
    private final javafx.scene.control.MenuButton perfil = new javafx.scene.control.MenuButton();
    private final TextField buscaGlobal = new TextField();
    private final ContextoDaInterface ui;
    private Scene cena;

    public JanelaPrincipal(ContextoLocal local, GerenciadorDeLicenca licenca, Consumer<String> navegador) {
        this.local = local;
        this.licenca = licenca;
        this.ui = new ContextoDaInterface(local, licenca, notificador, () -> cena == null ? null : cena.getWindow(), navegador);
        Roteador roteador = new Roteador(area, d -> Telas.criar(d, ui), ui::reduzirAnimacoes);
        ui.definirRoteador(roteador);
        ui.aoMudarEstado(this::atualizarCabecalho);
        ui.aoBuscar(texto -> Telas.abrirRelacionamentos(ui, VisaoDeRelacionamento.SEGUIDORES, texto));

        raiz.getStyleClass().add("janela");
        raiz.setLeft(barraLateral());
        VBox centro = new VBox(barraSuperior(), conteudo());
        VBox.setVgrow(centro.getChildren().get(1), Priority.ALWAYS);
        raiz.setCenter(centro);
        roteador.aoNavegar(d -> {
            ToggleButton b = botoes.get(d);
            if (b != null) {
                b.setSelected(true);
            } else {
                grupo.selectToggle(null);
            }
        });
        atualizarCabecalho();
    }

    public Scene criarCena() {
        cena = new Scene(raiz, 1280, 800);
        aplicarTema();
        registrarAtalhos();
        boolean primeiraVez = local.relacionamentos().painel().isEmpty();
        ui.roteador().ir(primeiraVez ? Destino.BOAS_VINDAS : Destino.PAINEL);
        area.setFocusTraversable(true);
        area.requestFocus();
        return cena;
    }

    public ContextoDaInterface contexto() {
        return ui;
    }

    public Window janela() {
        return cena == null ? null : cena.getWindow();
    }

    // ---- construção ----

    private VBox barraLateral() {
        Circle ponto = new Circle(9);
        ponto.getStyleClass().add("logo-ponto");
        Label nome = new Label("InstaDino");
        nome.getStyleClass().add("logo-texto");
        HBox logo = new HBox(10, ponto, nome);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.setPadding(new Insets(22, 18, 18, 18));

        VBox menu = new VBox(2, logo,
                secao("ANÁLISE"),
                item(Destino.PAINEL, "painel", "Painel"),
                item(Destino.IMPORTACAO, "importar", "Importar"),
                item(Destino.RELACIONAMENTOS, "usuarios", "Relacionamentos"),
                item(Destino.HISTORICO, "historico", "Histórico"),
                item(Destino.RELATORIOS, "relatorio", "Relatórios"),
                secao("CONTA"),
                item(Destino.CONTA, "usuario", "Conta"),
                item(Destino.PREMIUM, "estrela", "Premium"),
                secao("SISTEMA"),
                item(Destino.SEGURANCA, "escudo", "Segurança e privacidade"),
                item(Destino.CONFIGURACOES, "ajustes", "Configurações"));
        menu.setPadding(new Insets(0, 12, 12, 12));
        Region espaco = new Region();
        VBox.setVgrow(espaco, Priority.ALWAYS);
        Label rodape = Ui.pequeno("Processamento 100% local");
        rodape.setPadding(new Insets(0, 18, 18, 18));
        VBox barra = new VBox(menu, espaco, rodape);
        barra.getStyleClass().add("barra-lateral");
        barra.setPrefWidth(284);
        barra.setMinWidth(284);
        return barra;
    }

    private static Label secao(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("secao-nav");
        return l;
    }

    private ToggleButton item(Destino destino, String icone, String texto) {
        ToggleButton b = new ToggleButton(texto, Icones.de(icone));
        b.getStyleClass().add("botao-nav");
        b.setToggleGroup(grupo);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAccessibleText(texto);
        b.setOnAction(e -> {
            if (ui.roteador().atual() != destino) {
                ui.roteador().ir(destino);
            }
            b.setSelected(true);
        });
        botoes.put(destino, b);
        return b;
    }

    private HBox barraSuperior() {
        perfil.getStyleClass().add("botao-perfil");
        perfil.setAccessibleText("Perfil local ativo. Abre o menu para trocar, criar, renomear ou excluir perfis");
        buscaGlobal.setPromptText("Buscar contas…  (Ctrl+F)");
        buscaGlobal.getStyleClass().add("campo-global");
        buscaGlobal.setAccessibleText("Busca global de contas");
        buscaGlobal.setOnAction(e -> {
            ui.buscarNaListaDeContas(buscaGlobal.getText());
            buscaGlobal.clear();
        });
        seloDoPlano.getStyleClass().add("selo");
        seloDoPlano.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        botaoDaConta.getStyleClass().add("botao-link");
        botaoDaConta.setOnAction(e -> ui.roteador().ir(Destino.CONTA));
        HBox barra = new HBox(14, perfil, espaco, buscaGlobal, seloDoPlano, botaoDaConta);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(12, 24, 12, 24));
        barra.getStyleClass().add("barra-superior");
        return barra;
    }

    private StackPane conteudo() {
        StackPane pilha = new StackPane(area, notificador);
        StackPane.setAlignment(notificador, Pos.BOTTOM_RIGHT);
        notificador.setPickOnBounds(false);
        return pilha;
    }

    private void registrarAtalhos() {
        Destino[] ordem = {Destino.PAINEL, Destino.IMPORTACAO, Destino.RELACIONAMENTOS, Destino.HISTORICO, Destino.RELATORIOS,
            Destino.CONTA, Destino.PREMIUM, Destino.SEGURANCA, Destino.CONFIGURACOES};
        KeyCode[] teclas = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5, KeyCode.DIGIT6,
            KeyCode.DIGIT7, KeyCode.DIGIT8, KeyCode.DIGIT9};
        for (int i = 0; i < ordem.length; i++) {
            Destino d = ordem[i];
            cena.getAccelerators().put(new KeyCodeCombination(teclas[i], KeyCombination.SHORTCUT_DOWN), () -> ui.roteador().ir(d));
        }
        cena.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), buscaGlobal::requestFocus);
    }

    // ---- estado ----

    private void atualizarCabecalho() {
        Plano plano = local.politica().planoEfetivo();
        seloDoPlano.setText(plano == Plano.PREMIUM ? "PREMIUM" : "FREE");
        seloDoPlano.getStyleClass().removeAll("selo-free", "selo-premium");
        seloDoPlano.getStyleClass().add(plano == Plano.PREMIUM ? "selo-premium" : "selo-free");
        seloDoPlano.setAccessibleText("Plano " + (plano == Plano.PREMIUM ? "Premium" : "Free"));
        reconstruirMenuDePerfis();
        botaoDaConta.setText(licenca.autenticado() ? licenca.conta().map(c -> c.nome()).orElse("Minha conta") : "Entrar");
        botaoDaConta.setAccessibleText(licenca.autenticado() ? "Abrir minha conta" : "Entrar na conta comercial (opcional)");
        if (cena != null) {
            aplicarTema();
        }
    }

    // ---- perfis locais ----

    private void reconstruirMenuDePerfis() {
        var servico = local.servicoDePerfis();
        var ativo = servico.ativo();
        perfil.setText("Perfil: " + ativo.nome());
        perfil.getItems().clear();
        javafx.scene.control.ToggleGroup selecao = new javafx.scene.control.ToggleGroup();
        for (var p : servico.listar()) {
            javafx.scene.control.RadioMenuItem item = new javafx.scene.control.RadioMenuItem(p.nome());
            item.setToggleGroup(selecao);
            item.setSelected(p.id() == ativo.id());
            item.setOnAction(e -> trocarDePerfil(p.id()));
            perfil.getItems().add(item);
        }
        perfil.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        javafx.scene.control.MenuItem novo = new javafx.scene.control.MenuItem("Novo perfil…");
        novo.setOnAction(e -> novoPerfil());
        javafx.scene.control.MenuItem renomear = new javafx.scene.control.MenuItem("Renomear este perfil…");
        renomear.setOnAction(e -> renomearPerfil());
        javafx.scene.control.MenuItem excluir = new javafx.scene.control.MenuItem("Excluir este perfil…");
        excluir.setDisable(servico.listar().size() <= 1);
        excluir.setOnAction(e -> excluirPerfil());
        perfil.getItems().addAll(novo, renomear, excluir);
    }

    private void trocarDePerfil(long id) {
        try {
            local.servicoDePerfis().selecionar(id);
        } catch (RuntimeException e) {
            ui.notificador().erro("Não foi possível trocar de perfil.");
        }
        recarregarTelaAtual();
    }

    private void novoPerfil() {
        if (!local.politica().permite(br.com.instadino.nucleo.acesso.Funcionalidade.MULTIPLOS_PERFIS) && local.servicoDePerfis().listar().size() >= 1) {
            ui.notificador().info("Mais de um perfil local faz parte do plano Premium.");
            ui.roteador().ir(Destino.PREMIUM);
            return;
        }
        String nome = pedirTexto("Novo perfil", "Dê um nome ao perfil (por exemplo, a conta que será analisada):", "");
        if (nome == null) {
            return;
        }
        try {
            local.servicoDePerfis().criar(nome);
            ui.notificador().sucesso("Perfil criado e selecionado.");
        } catch (RuntimeException e) {
            ui.notificador().erro(e.getMessage() == null ? "Não foi possível criar o perfil." : e.getMessage());
        }
        recarregarTelaAtual();
    }

    private void renomearPerfil() {
        var ativo = local.servicoDePerfis().ativo();
        String nome = pedirTexto("Renomear perfil", "Novo nome do perfil:", ativo.nome());
        if (nome == null) {
            return;
        }
        try {
            local.servicoDePerfis().renomear(ativo.id(), nome);
        } catch (RuntimeException e) {
            ui.notificador().erro(e.getMessage() == null ? "Não foi possível renomear o perfil." : e.getMessage());
        }
        recarregarTelaAtual();
    }

    private void excluirPerfil() {
        var ativo = local.servicoDePerfis().ativo();
        javafx.scene.control.Alert confirmacao = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Excluir o perfil \"" + ativo.nome() + "\" apaga todas as importações, favoritos, etiquetas e notas dele. A ação não pode ser desfeita.",
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        confirmacao.setHeaderText("Excluir este perfil?");
        confirmacao.initOwner(janela());
        if (confirmacao.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) != javafx.scene.control.ButtonType.OK) {
            return;
        }
        try {
            local.servicoDePerfis().excluir(ativo.id());
            ui.notificador().sucesso("Perfil excluído.");
        } catch (RuntimeException e) {
            ui.notificador().erro(e.getMessage() == null ? "Não foi possível excluir o perfil." : e.getMessage());
        }
        recarregarTelaAtual();
    }

    private String pedirTexto(String titulo, String cabecalho, String inicial) {
        javafx.scene.control.TextInputDialog dialogo = new javafx.scene.control.TextInputDialog(inicial);
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(cabecalho);
        dialogo.initOwner(janela());
        return dialogo.showAndWait().map(String::strip).filter(t -> !t.isEmpty()).orElse(null);
    }

    /** Depois de trocar o perfil ativo, todas as telas precisam reler os dados do novo perfil. */
    private void recarregarTelaAtual() {
        ui.estadoMudou();
        var destino = ui.roteador().atual();
        if (destino != null) {
            ui.roteador().ir(destino);
        }
    }

    private void aplicarTema() {
        boolean claro = "claro".equals(local.preferencias().obter("tema").orElse("escuro"));
        var folhas = cena.getStylesheets();
        folhas.clear();
        folhas.add(getClass().getResource(CSS_ESCURO).toExternalForm());
        if (claro) {
            folhas.add(getClass().getResource(CSS_CLARO).toExternalForm());
        }
    }
}
