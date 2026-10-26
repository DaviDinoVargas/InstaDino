package br.com.instadino.servidor.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de taxa por chave com janela fixa, em memória. Adequado para UMA instância do servidor; em produção com
 * várias instâncias é necessário um armazenamento compartilhado (risco residual registrado em docs/security).
 */
public final class LimitadorDeTaxa {

    private record Janela(Instant inicio, int contagem) {
    }

    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();
    private final Clock relogio;
    private final int limite;
    private final Duration duracao;

    public LimitadorDeTaxa(Clock relogio, int limite, Duration duracao) {
        this.relogio = relogio;
        this.limite = limite;
        this.duracao = duracao;
    }

    /** Registra uma requisição; devolve false se o limite da janela foi excedido. */
    public boolean permitir(String chave) {
        Instant agora = relogio.instant();
        if (janelas.size() > 50_000) {
            janelas.entrySet().removeIf(e -> e.getValue().inicio().plus(duracao).isBefore(agora));
        }
        Janela nova = janelas.compute(chave, (k, atual) -> {
            if (atual == null || !atual.inicio().plus(duracao).isAfter(agora)) {
                return new Janela(agora, 1);
            }
            return new Janela(atual.inicio(), atual.contagem() + 1);
        });
        return nova.contagem() <= limite;
    }

    public long segundosAteLiberar(String chave) {
        Janela j = janelas.get(chave);
        if (j == null) {
            return 0;
        }
        return Math.max(1, Duration.between(relogio.instant(), j.inicio().plus(duracao)).getSeconds());
    }
}
