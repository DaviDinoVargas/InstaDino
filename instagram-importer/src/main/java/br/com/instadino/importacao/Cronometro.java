package br.com.instadino.importacao;

import java.time.Duration;

/**
 * Cronômetro monotônico para o limite de tempo da importação (IMP-07). Não usa o relógio de parede: ele pode
 * retroceder ou saltar (sincronização de hora, ajuste manual), o que estenderia ou anteciparia o limite indevidamente.
 */
final class Cronometro {

    private final long inicio = System.nanoTime();

    Duration decorrido() {
        return Duration.ofNanos(System.nanoTime() - inicio);
    }
}
