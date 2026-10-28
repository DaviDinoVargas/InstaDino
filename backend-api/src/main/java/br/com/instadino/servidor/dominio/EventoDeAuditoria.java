package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Trilha de auditoria somente de inclusão: não há operações de alteração ou exclusão no código. */
@Entity
@Table(name = "auditoria")
public class EventoDeAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private Instant ocorridoEm;
    @Column(name = "ator_id", updatable = false)
    private UUID atorId;
    @Column(name = "ator_tipo", nullable = false, updatable = false)
    private String atorTipo;
    @Column(nullable = false, updatable = false)
    private String acao;
    @Column(updatable = false)
    private String alvo;
    @Column(updatable = false)
    private String detalhe;

    protected EventoDeAuditoria() {
    }

    public EventoDeAuditoria(Instant ocorridoEm, UUID atorId, String atorTipo, String acao, String alvo, String detalhe) {
        this.ocorridoEm = ocorridoEm;
        this.atorId = atorId;
        this.atorTipo = atorTipo;
        this.acao = acao;
        this.alvo = alvo;
        this.detalhe = detalhe;
    }

    public Long getId() {
        return id;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public UUID getAtorId() {
        return atorId;
    }

    public String getAtorTipo() {
        return atorTipo;
    }

    public String getAcao() {
        return acao;
    }

    public String getAlvo() {
        return alvo;
    }

    public String getDetalhe() {
        return detalhe;
    }
}
