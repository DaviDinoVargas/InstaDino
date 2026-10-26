package br.com.instadino.desktop.navegacao;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Troca a tela exibida, criando cada tela uma única vez. */
public final class Roteador {

    private final StackPane area;
    private final Function<Destino, Tela> fabrica;
    private final java.util.function.BooleanSupplier reduzirAnimacoes;
    private final Map<Destino, Tela> telas = new EnumMap<>(Destino.class);
    private Consumer<Destino> aoNavegar = d -> { };
    private Destino atual;

    public Roteador(StackPane area, Function<Destino, Tela> fabrica, java.util.function.BooleanSupplier reduzirAnimacoes) {
        this.area = area;
        this.fabrica = fabrica;
        this.reduzirAnimacoes = reduzirAnimacoes;
    }

    public void aoNavegar(Consumer<Destino> ouvinte) {
        this.aoNavegar = ouvinte;
    }

    public Destino atual() {
        return atual;
    }

    public Tela tela(Destino destino) {
        return telas.computeIfAbsent(destino, fabrica);
    }

    public void ir(Destino destino) {
        Tela tela = tela(destino);
        Node conteudo = tela.conteudo();
        atual = destino;
        area.getChildren().setAll(conteudo);
        tela.aoExibir();
        aoNavegar.accept(destino);
        if (!reduzirAnimacoes.getAsBoolean()) {
            conteudo.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(140), conteudo);
            fade.setToValue(1);
            fade.play();
        }
    }
}
