package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessao")
public class Sessao {

    @Id
    private UUID id;
    @Column(name = "conta_id", nullable = false)
    private UUID contaId;
    @Column(name = "hash_token", nullable = false)
    private String hashToken;
    @Column(name = "dispositivo_id", nullable = false)
    private String dispositivoId;
    @Column(name = "nome_dispositivo")
    private String nomeDispositivo;
    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;
    @Column(name = "ultimo_uso", nullable = false)
    private Instant ultimoUso;
    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;
    @Column(name = "revogada_em")
    private Instant revogadaEm;

    protected Sessao() {
    }

    public Sessao(UUID contaId, String hashToken, String dispositivoId, String nomeDispositivo, Instant agora, Instant expiraEm) {
        this.id = UUID.randomUUID();
        this.contaId = contaId;
        this.hashToken = hashToken;
        this.dispositivoId = dispositivoId;
        this.nomeDispositivo = nomeDispositivo;
        this.criadaEm = agora;
        this.ultimoUso = agora;
        this.expiraEm = expiraEm;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContaId() {
        return contaId;
    }

    public String getDispositivoId() {
        return dispositivoId;
    }

    public String getNomeDispositivo() {
        return nomeDispositivo;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getUltimoUso() {
        return ultimoUso;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getRevogadaEm() {
        return revogadaEm;
    }

    public boolean valida(Instant agora) {
        return revogadaEm == null && expiraEm.isAfter(agora);
    }

    public void tocar(Instant agora) {
        this.ultimoUso = agora;
    }

    public void revogar(Instant agora) {
        if (revogadaEm == null) {
            revogadaEm = agora;
        }
    }
}
