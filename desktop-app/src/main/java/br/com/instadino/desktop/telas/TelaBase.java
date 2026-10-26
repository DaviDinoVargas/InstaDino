package br.com.instadino.desktop.telas;

import br.com.instadino.desktop.ContextoDaInterface;
import br.com.instadino.desktop.componentes.Ui;
import br.com.instadino.desktop.navegacao.Tela;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/** Estrutura comum: área rolável com margens, título e subtítulo. */
abstract class TelaBase implements Tela {

    protected final ContextoDaInterface ui;
    private final ScrollPane rolagem = new ScrollPane();
    protected final VBox pagina = new VBox(20);

    TelaBase(ContextoDaInterface ui, String titulo, String subtitulo) {
        this.ui = ui;
        pagina.setPadding(new Insets(28, 32, 32, 32));
        pagina.getChildren().add(cabecalho(titulo, subtitulo));
        rolagem.setContent(pagina);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.setPannable(false);
    }

    private static Node cabecalho(String titulo, String subtitulo) {
        VBox caixa = new VBox(4, Ui.titulo(titulo));
        if (subtitulo != null && !subtitulo.isBlank()) {
            caixa.getChildren().add(Ui.subtitulo(subtitulo));
        }
        return caixa;
    }

    @Override
    public Node conteudo() {
        return rolagem;
    }

    /** Remove tudo exceto o cabeçalho, para redesenhar o corpo da tela. */
    protected void limparCorpo() {
        if (pagina.getChildren().size() > 1) {
            pagina.getChildren().remove(1, pagina.getChildren().size());
        }
    }

    protected void corpo(Node... nos) {
        pagina.getChildren().addAll(nos);
    }
}
