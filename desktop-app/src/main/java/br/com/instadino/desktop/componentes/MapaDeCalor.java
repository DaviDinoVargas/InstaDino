package br.com.instadino.desktop.componentes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Mapa de calor dia da semana × hora. A intensidade é proporcional ao maior valor da grade; cada célula tem a contagem na dica. */
public final class MapaDeCalor {

    private static final String[] DIAS = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};

    private MapaDeCalor() {
    }

    public static VBox criar(int[][] grade) {
        int maximo = 1;
        for (int[] linha : grade) {
            for (int v : linha) {
                maximo = Math.max(maximo, v);
            }
        }
        GridPane g = new GridPane();
        g.setHgap(3);
        g.setVgap(3);
        for (int h = 0; h < 24; h++) {
            Label rotulo = new Label(h % 3 == 0 ? String.format("%02d", h) : "");
            rotulo.getStyleClass().add("texto-suave");
            rotulo.setStyle("-fx-font-size: 10px;");
            GridPane.setHalignment(rotulo, javafx.geometry.HPos.CENTER);
            g.add(rotulo, h + 1, 0);
        }
        for (int d = 0; d < 7; d++) {
            Label dia = new Label(DIAS[d]);
            dia.getStyleClass().add("texto-suave");
            g.add(dia, 0, d + 1);
            for (int h = 0; h < 24; h++) {
                int valor = grade[d][h];
                Region celula = new Region();
                celula.setMinSize(22, 22);
                celula.setPrefSize(22, 22);
                double intensidade = valor == 0 ? 0 : 0.18 + 0.82 * valor / maximo;
                celula.setStyle("-fx-background-radius: 4; -fx-background-color: " + (valor == 0 ? "rgba(255,255,255,0.05)"
                        : String.format(java.util.Locale.ROOT, "rgba(139,92,246,%.2f)", intensidade)) + ";");
                Tooltip.install(celula, new Tooltip(DIAS[d] + ", " + String.format("%02d", h) + "h: " + valor));
                celula.setAccessibleText(DIAS[d] + " às " + h + " horas: " + valor + " registros");
                g.add(new StackPane(celula), h + 1, d + 1);
            }
        }
        Label legenda = new Label("Mais escuro = mais registros (máximo em uma célula: " + maximo + ")");
        legenda.getStyleClass().add("texto-suave");
        VBox caixa = new VBox(8, g, legenda);
        caixa.setPadding(new Insets(4));
        caixa.setAlignment(Pos.TOP_LEFT);
        return caixa;
    }
}
