package br.com.instadino.desktop.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;

/** Executa trabalho fora da thread da interface, com progresso e cancelamento reais. */
public final class Tarefas {

    /** Contexto repassado ao trabalho para informar o progresso. */
    public interface Progresso {
        void atualizar(String mensagem, long atual, long total);
    }

    /** Trabalho que produz um resultado e pode lançar exceções. */
    @FunctionalInterface
    public interface Trabalho<T> {
        T executar(Progresso progresso) throws Exception;
    }

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "instadino-tarefa");
        t.setDaemon(true);
        return t;
    });

    private Tarefas() {
    }

    public static <T> Task<T> executar(Trabalho<T> trabalho, Consumer<T> aoConcluir, Consumer<Throwable> aoFalhar) {
        Task<T> tarefa = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabalho.executar((mensagem, atual, total) -> {
                    updateMessage(mensagem);
                    updateProgress(atual, Math.max(total, 1));
                });
            }
        };
        tarefa.setOnSucceeded(e -> aoConcluir.accept(tarefa.getValue()));
        tarefa.setOnFailed(e -> aoFalhar.accept(tarefa.getException()));
        EXECUTOR.execute(tarefa);
        return tarefa;
    }

    public static void naInterface(Runnable acao) {
        if (Platform.isFxApplicationThread()) {
            acao.run();
        } else {
            Platform.runLater(acao);
        }
    }
}
