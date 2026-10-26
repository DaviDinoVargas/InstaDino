package br.com.instadino.desktop.componentes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Fábrica de componentes visuais padronizados. */
public final class Ui {

    private Ui() {
    }

    public static Label titulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("titulo-tela");
        l.setWrapText(true);
        return l;
    }

    public static Label subtitulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("subtitulo-tela");
        l.setWrapText(true);
        return l;
    }

    public static Label tituloDeCartao(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("titulo-cartao");
        l.setWrapText(true);
        return l;
    }

    public static Label suave(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-suave");
        l.setWrapText(true);
        return l;
    }

    public static Label pequeno(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-pequeno");
        l.setWrapText(true);
        return l;
    }

    public static VBox cartao(Node... filhos) {
        VBox caixa = new VBox(12, filhos);
        caixa.getStyleClass().add("cartao");
        return caixa;
    }

    public static VBox cartaoDestaque(Node... filhos) {
        VBox caixa = new VBox(12, filhos);
        caixa.getStyleClass().add("cartao-destaque");
        return caixa;
    }

    /** Cartão de métrica: rótulo, valor grande e detalhe opcional. */
    public static VBox metrica(String rotulo, Label valor, String detalhe) {
        Label r = new Label(rotulo);
        r.getStyleClass().add("metrica-rotulo");
        r.setWrapText(true);
        valor.getStyleClass().add("metrica-valor");
        VBox caixa = new VBox(4, r, valor);
        if (detalhe != null && !detalhe.isBlank()) {
            Label d = new Label(detalhe);
            d.getStyleClass().add("metrica-detalhe");
            d.setWrapText(true);
            caixa.getChildren().add(d);
        }
        caixa.getStyleClass().add("cartao");
        caixa.setMinWidth(190);
        caixa.setAccessibleText(rotulo);
        return caixa;
    }

    public static VBox estadoVazio(String titulo, String texto, Node acao) {
        Label t = new Label(titulo);
        t.getStyleClass().add("estado-vazio-titulo");
        t.setWrapText(true);
        Label d = suave(texto);
        d.setMaxWidth(480);
        d.setAlignment(Pos.CENTER);
        d.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        VBox caixa = new VBox(10, t, d);
        if (acao != null) {
            caixa.getChildren().add(acao);
        }
        caixa.getStyleClass().add("estado-vazio");
        caixa.setAlignment(Pos.CENTER);
        return caixa;
    }

    /** Caixa colorida de aviso, erro ou informação. */
    public static HBox caixa(Tipo tipo, String texto) {
        Label l = new Label(texto);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(l, Priority.ALWAYS);
        HBox caixa = new HBox(l);
        caixa.getStyleClass().add(switch (tipo) {
            case AVISO -> "aviso-caixa";
            case ERRO -> "erro-caixa";
            case INFO -> "info-caixa";
        });
        caixa.setAccessibleText(texto);
        return caixa;
    }

    public enum Tipo { AVISO, ERRO, INFO }

    public static Button primario(String texto, Runnable acao) {
        Button b = new Button(texto);
        b.getStyleClass().add("botao-primario");
        b.setOnAction(e -> acao.run());
        return b;
    }

    public static Button secundario(String texto, Runnable acao) {
        Button b = new Button(texto);
        b.setOnAction(e -> acao.run());
        return b;
    }

    public static Button perigo(String texto, Runnable acao) {
        Button b = secundario(texto, acao);
        b.getStyleClass().add("botao-perigo");
        return b;
    }

    public static Button link(String texto, Runnable acao) {
        Button b = secundario(texto, acao);
        b.getStyleClass().add("botao-link");
        return b;
    }

    public static void dica(Node no, String texto) {
        Tooltip.install(no, new Tooltip(texto));
        no.setAccessibleHelp(texto);
    }

    public static Insets margem(double v) {
        return new Insets(v);
    }
}
