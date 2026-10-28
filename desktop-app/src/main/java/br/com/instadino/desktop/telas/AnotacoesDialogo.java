package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Destino;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes.Anotacao;
import br.com.instadino.persistencia.repositorio.RepositorioDeAnotacoes.Etiqueta;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Edição de favorito, etiquetas e nota privada de uma conta. Sem o Premium o diálogo mostra o que já existe, sem
 * permitir criar ou alterar (remover continua permitido). A gravação passa sempre pelos serviços, que reaplicam a política.
 */
final class AnotacoesDialogo {

    private final ContextoDaInterface ui;
    private final String usuario;
    private final Anotacao atual;
    private final boolean premium;
    private final CheckBox favorito = new CheckBox("Favorita");
    private final TextArea nota = new TextArea();
    private final Map<Long, CheckBox> caixasDeEtiqueta = new LinkedHashMap<>();
    private final FlowPane etiquetas = new FlowPane(10, 8);
    private final TextField novaEtiqueta = new TextField();
    private final Label erro = new Label();

    AnotacoesDialogo(ContextoDaInterface ui, String usuario) {
        this.ui = ui;
        this.usuario = usuario;
        this.atual = ui.local().anotacoes().doPerfilAtivo().getOrDefault(usuario, new Anotacao(usuario, false, null, List.of()));
        this.premium = ui.local().politica().permite(Funcionalidade.FAVORITOS) && ui.local().politica().permite(Funcionalidade.ETIQUETAS)
                && ui.local().politica().permite(Funcionalidade.NOTAS_PRIVADAS);
        favorito.setSelected(atual.favorito());
        nota.setText(atual.nota() == null ? "" : atual.nota());
        nota.setPromptText("Uma nota só sua. Fica neste computador, cifrada, e nunca sai dele.");
        nota.setPrefRowCount(5);
        nota.setWrapText(true);
        nota.setAccessibleText("Nota privada sobre @" + usuario);
        novaEtiqueta.setPromptText("Nova etiqueta");
        novaEtiqueta.setAccessibleText("Nome da nova etiqueta");
        erro.getStyleClass().add("texto-erro");
        erro.setWrapText(true);
        erro.setVisible(false);
        erro.setManaged(false);
        recarregarEtiquetas();
        if (!premium) {
            favorito.setDisable(atual.nota() == null && !atual.favorito());
            nota.setEditable(false);
            novaEtiqueta.setDisable(true);
        }
    }

    Dialog<ButtonType> dialogo() {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Anotações");
        d.setHeaderText("Anotações sobre @" + usuario);
        d.initOwner(ui.janela());
        VBox corpo = new VBox(12);
        corpo.setPadding(new Insets(4, 0, 0, 0));
        corpo.setPrefWidth(480);
        if (!premium) {
            corpo.getChildren().add(Ui.caixa(Ui.Tipo.AVISO, "Favoritos, etiquetas e notas privadas fazem parte do plano Premium. "
                    + "O que você já guardou continua visível e pode ser removido."));
            corpo.getChildren().add(Ui.link("Conhecer o Premium", () -> {
                d.close();
                ui.roteador().ir(Destino.PREMIUM);
            }));
        }
        Button criar = Ui.secundario("Criar", this::criarEtiqueta);
        criar.setDisable(!premium);
        novaEtiqueta.setOnAction(e -> criarEtiqueta());
        Button gerenciar = Ui.link("Gerenciar etiquetas…", this::gerenciarEtiquetas);
        gerenciar.setDisable(ui.local().anotacoes().etiquetas().isEmpty());
        Label contador = Ui.pequeno("");
        nota.textProperty().addListener((o, a, n) -> contador.setText(n.length() + " / " + RepositorioDeAnotacoes.TAMANHO_MAXIMO_DA_NOTA));
        nota.setTextFormatter(new javafx.scene.control.TextFormatter<>(c ->
                c.getControlNewText().length() <= RepositorioDeAnotacoes.TAMANHO_MAXIMO_DA_NOTA ? c : null));
        contador.setText(nota.getText().length() + " / " + RepositorioDeAnotacoes.TAMANHO_MAXIMO_DA_NOTA);
        corpo.getChildren().addAll(favorito, Ui.tituloDeCartao("Etiquetas"), etiquetas,
                new HBox(8, novaEtiqueta, criar, gerenciar), Ui.tituloDeCartao("Nota privada"), nota, contador, erro);
        d.getDialogPane().setContent(corpo);
        ButtonType salvar = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(salvar, ButtonType.CANCEL);
        d.getDialogPane().lookupButton(salvar).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (!aplicar()) {
                e.consume();
            }
        });
        return d;
    }

    /** Grava o estado atual dos campos. Devolve falso (e mostra o motivo) se algo foi recusado. */
    boolean aplicar() {
        try {
            var servico = ui.local().anotacoes();
            if (favorito.isSelected() != atual.favorito()) {
                servico.favoritar(usuario, favorito.isSelected());
            }
            String texto = nota.getText() == null ? "" : nota.getText().strip();
            if (!texto.equals(atual.nota() == null ? "" : atual.nota())) {
                servico.definirNota(usuario, texto);
            }
            Set<Long> antes = atual.etiquetas().stream().map(Etiqueta::id).collect(Collectors.toSet());
            for (Map.Entry<Long, CheckBox> e : caixasDeEtiqueta.entrySet()) {
                boolean marcada = e.getValue().isSelected();
                if (marcada && !antes.contains(e.getKey())) {
                    servico.aplicarEtiqueta(usuario, e.getKey());
                } else if (!marcada && antes.contains(e.getKey())) {
                    servico.removerEtiqueta(usuario, e.getKey());
                }
            }
            return true;
        } catch (RuntimeException e) {
            mostrarErro(Mensagens.paraUsuario(e));
            return false;
        }
    }

    private void criarEtiqueta() {
        String nome = novaEtiqueta.getText();
        if (nome == null || nome.isBlank()) {
            return;
        }
        try {
            Etiqueta nova = ui.local().anotacoes().criarEtiqueta(nome);
            Set<Long> marcadas = caixasDeEtiqueta.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            marcadas.add(nova.id());
            novaEtiqueta.clear();
            erro.setVisible(false);
            erro.setManaged(false);
            recarregarEtiquetas();
            caixasDeEtiqueta.forEach((id, caixa) -> caixa.setSelected(marcadas.contains(id)));
        } catch (RuntimeException e) {
            mostrarErro(e instanceof IllegalArgumentException ? e.getMessage() : Mensagens.paraUsuario(e));
        }
    }

    private void gerenciarEtiquetas() {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Gerenciar etiquetas");
        d.setHeaderText("Renomear ou excluir etiquetas deste perfil");
        d.initOwner(ui.janela());
        VBox lista = new VBox(8);
        lista.setPrefWidth(420);
        Runnable[] reconstruir = new Runnable[1];
        reconstruir[0] = () -> {
            lista.getChildren().clear();
            for (Etiqueta e : ui.local().anotacoes().etiquetas()) {
                TextField campo = new TextField(e.nome());
                campo.setPrefWidth(220);
                campo.setDisable(!premium);
                Button renomear = Ui.secundario("Renomear", () -> {
                    try {
                        ui.local().anotacoes().renomearEtiqueta(e.id(), campo.getText());
                    } catch (RuntimeException ex) {
                        ui.notificador().erro(ex instanceof IllegalArgumentException ? ex.getMessage() : Mensagens.paraUsuario(ex));
                    }
                    reconstruir[0].run();
                });
                renomear.setDisable(!premium);
                Button excluir = Ui.perigo("Excluir", () -> {
                    ui.local().anotacoes().excluirEtiqueta(e.id());
                    reconstruir[0].run();
                });
                lista.getChildren().add(new HBox(8, campo, renomear, excluir));
            }
            if (lista.getChildren().isEmpty()) {
                lista.getChildren().add(Ui.suave("Nenhuma etiqueta."));
            }
        };
        reconstruir[0].run();
        d.getDialogPane().setContent(lista);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
        recarregarEtiquetas();
    }

    private void recarregarEtiquetas() {
        Set<Long> aplicadas = atual.etiquetas().stream().map(Etiqueta::id).collect(Collectors.toSet());
        Map<Long, Boolean> marcadasAgora = new LinkedHashMap<>();
        caixasDeEtiqueta.forEach((id, c) -> marcadasAgora.put(id, c.isSelected()));
        caixasDeEtiqueta.clear();
        etiquetas.getChildren().clear();
        List<Etiqueta> todas = ui.local().anotacoes().etiquetas();
        for (Etiqueta e : todas) {
            CheckBox c = new CheckBox(e.nome());
            c.setSelected(marcadasAgora.getOrDefault(e.id(), aplicadas.contains(e.id())));
            c.setDisable(!premium && !aplicadas.contains(e.id()));
            caixasDeEtiqueta.put(e.id(), c);
            etiquetas.getChildren().add(c);
        }
        if (todas.isEmpty()) {
            etiquetas.getChildren().add(Ui.pequeno("Você ainda não criou etiquetas."));
        }
    }

    private void mostrarErro(String mensagem) {
        erro.setText(mensagem);
        erro.setVisible(true);
        erro.setManaged(true);
    }

    // ---- acesso para testes ----

    CheckBox campoFavorito() {
        return favorito;
    }

    TextArea campoNota() {
        return nota;
    }

    TextField campoNovaEtiqueta() {
        return novaEtiqueta;
    }

    Map<Long, CheckBox> caixasDeEtiqueta() {
        return caixasDeEtiqueta;
    }

    void criarEtiquetaDoCampo() {
        criarEtiqueta();
    }
}
