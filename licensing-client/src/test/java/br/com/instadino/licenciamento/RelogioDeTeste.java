package br.com.instadino.licenciamento;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class RelogioDeTeste extends Clock {

    private Instant agora = Instant.parse("2026-03-01T12:00:00Z");

    void avancar(Duration d) {
        agora = agora.plus(d);
    }

    void definir(Instant i) {
        agora = i;
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
        return agora;
    }
}
