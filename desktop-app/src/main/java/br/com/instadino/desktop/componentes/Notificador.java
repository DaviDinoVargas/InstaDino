package br.com.instadino.desktop.componentes;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Notificações discretas no canto inferior direito, que desaparecem sozinhas. */
public final class Notificador extends VBox {

    public enum Nivel { INFO, SUCESSO, ERRO }

    public Notificador() {
        setSpacing(8);
        setPickOnBounds(false);
        setMouseTransparent(false);
        setAlignment(Pos.BOTTOM_RIGHT);
        setMaxWidth(420);
        StackPane.setAlignment(this, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(this, new javafx.geometry.Insets(0, 24, 24, 0));
    }

    public void mostrar(String mensagem, Nivel nivel) {
        Label l = new Label(mensagem);
        l.setWrapText(true);
        l.getStyleClass().add("notificacao");
        if (nivel == Nivel.ERRO) {
            l.getStyleClass().add("notificacao-erro");
        } else if (nivel == Nivel.SUCESSO) {
            l.getStyleClass().add("notificacao-sucesso");
        }
        l.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        l.setAccessibleText(mensagem);
        getChildren().add(l);
        PauseTransition pausa = new PauseTransition(Duration.seconds(nivel == Nivel.ERRO ? 7 : 4));
        pausa.setOnFinished(e -> getChildren().remove(l));
        pausa.play();
        l.setOnMouseClicked(e -> getChildren().remove(l));
    }

    public void info(String mensagem) {
        mostrar(mensagem, Nivel.INFO);
    }

    public void sucesso(String mensagem) {
        mostrar(mensagem, Nivel.SUCESSO);
    }

    public void erro(String mensagem) {
        mostrar(mensagem, Nivel.ERRO);
    }
}
