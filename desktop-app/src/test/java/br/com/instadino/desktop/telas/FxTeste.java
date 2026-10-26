package br.com.instadino.desktop.telas;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Assumptions;

/** Utilidades para testar a interface JavaFX sob um display (real ou virtual). */
final class FxTeste {

    private static boolean iniciado;

    private FxTeste() {
    }

    /** Ignora (explicitamente) os testes de interface quando não há display disponível. */
    static synchronized void iniciar() {
        Assumptions.assumeTrue(System.getenv("DISPLAY") != null || System.getProperty("os.name").toLowerCase().contains("windows"),
                "Sem display: execute com xvfb-run para testar a interface");
        if (!iniciado) {
            try {
                CountDownLatch pronto = new CountDownLatch(1);
                Platform.startup(pronto::countDown);
                pronto.await(20, TimeUnit.SECONDS);
                Platform.setImplicitExit(false);
                iniciado = true;
            } catch (IllegalStateException jaIniciado) {
                iniciado = true;
            } catch (Exception | UnsatisfiedLinkError e) {
                Assumptions.abort("Toolkit gráfico indisponível: " + e.getClass().getSimpleName());
            }
        }
    }

    static <T> T naInterface(Callable<T> tarefa) {
        CompletableFuture<T> futuro = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                futuro.complete(tarefa.call());
            } catch (Throwable t) {
                futuro.completeExceptionally(t);
            }
        });
        try {
            return futuro.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("Falha na thread da interface: " + (e.getCause() != null ? e.getCause() : e), e.getCause() != null ? e.getCause() : e);
        }
    }

    static void naInterface(Runnable tarefa) {
        naInterface(() -> {
            tarefa.run();
            return null;
        });
    }

    static void esperar(BooleanSupplier condicao, String descricao) {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < limite) {
            if (naInterface(condicao::getAsBoolean)) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Tempo esgotado esperando: " + descricao);
    }

    static List<Node> todos(Node raiz) {
        List<Node> lista = new ArrayList<>();
        coletar(raiz, lista);
        return lista;
    }

    private static void coletar(Node no, List<Node> lista) {
        lista.add(no);
        if (no instanceof Parent p) {
            p.getChildrenUnmodifiable().forEach(f -> coletar(f, lista));
        }
    }

    static List<String> textos(Node raiz) {
        List<String> textos = new ArrayList<>();
        for (Node n : todos(raiz)) {
            if (n instanceof Labeled l && l.getText() != null && !l.getText().isBlank() && n.isVisible()) {
                textos.add(l.getText());
            }
        }
        return textos;
    }

    static boolean temTexto(Node raiz, String trecho) {
        return textos(raiz).stream().anyMatch(t -> t.contains(trecho));
    }

    static Button botao(Node raiz, String texto) {
        return todos(raiz).stream().filter(n -> n instanceof Button b && texto.equals(b.getText()) && b.isVisible())
                .map(n -> (Button) n).findFirst().orElse(null);
    }

    static void capturar(Node no, String nome) {
        Path pasta = Path.of("target", "capturas");
        try {
            Files.createDirectories(pasta);
            WritableImage imagem = naInterface(() -> {
                no.applyCss();
                if (no instanceof Parent p) {
                    p.layout();
                }
                return no.getScene() != null ? no.getScene().snapshot(null) : no.snapshot(null, null);
            });
            javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(imagem, null), "png", new File(pasta.toFile(), nome + ".png"));
        } catch (Exception e) {
            throw new AssertionError("Não foi possível salvar a captura " + nome, e);
        }
    }

    @SuppressWarnings("unused")
    private static boolean semDisplayJava() {
        return GraphicsEnvironment.isHeadless();
    }
}
