package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "licenca_emitida")
public class LicencaEmitida {

    @Id
    private UUID id;
    @Column(name = "conta_id", nullable = false)
    private UUID contaId;
    @Column(name = "dispositivo_id", nullable = false)
    private UUID dispositivoId;
    @Column(name = "emitida_em", nullable = false)
    private Instant emitidaEm;
    @Column(name = "valida_ate", nullable = false)
    private Instant validaAte;
    @Column(name = "tolerancia_ate", nullable = false)
    private Instant toleranciaAte;
    @Column(nullable = false)
    private String origem;
    @Column(name = "revogada_em")
    private Instant revogadaEm;

    protected LicencaEmitida() {
    }

    public LicencaEmitida(UUID contaId, UUID dispositivoId, Instant emitidaEm, Instant validaAte, Instant toleranciaAte, String origem) {
        this.id = UUID.randomUUID();
        this.contaId = contaId;
        this.dispositivoId = dispositivoId;
        this.emitidaEm = emitidaEm;
        this.validaAte = validaAte;
        this.toleranciaAte = toleranciaAte;
        this.origem = origem;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContaId() {
        return contaId;
    }

    public UUID getDispositivoId() {
        return dispositivoId;
    }

    public Instant getEmitidaEm() {
        return emitidaEm;
    }

    public Instant getValidaAte() {
        return validaAte;
    }

    public Instant getToleranciaAte() {
        return toleranciaAte;
    }

    public String getOrigem() {
        return origem;
    }

    public Instant getRevogadaEm() {
        return revogadaEm;
    }

    public void revogar(Instant agora) {
        if (revogadaEm == null) {
            revogadaEm = agora;
        }
    }
}
