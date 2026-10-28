package br.com.instadino.servidor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Relógio controlável pelos testes. */
public final class RelogioMutavel extends Clock {

    private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-03-01T12:00:00Z"));

    public void avancar(Duration d) {
        agora.updateAndGet(i -> i.plus(d));
    }

    public void definir(Instant instante) {
        agora.set(instante);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return agora.get();
    }
}
