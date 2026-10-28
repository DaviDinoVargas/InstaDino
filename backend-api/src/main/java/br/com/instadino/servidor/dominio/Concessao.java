package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Direito Premium concedido administrativamente (suporte, cortesia, contas de demonstração). Sempre auditado. */
@Entity
@Table(name = "concessao")
public class Concessao {

    @Id
    private UUID id;
    @Column(name = "conta_id", nullable = false)
    private UUID contaId;
    @Column(nullable = false)
    private String origem;
    @Column(nullable = false)
    private Instant inicio;
    private Instant fim;
    @Column(name = "concedida_por")
    private UUID concedidaPor;
    @Column(nullable = false)
    private String motivo;
    @Column(name = "revogada_em")
    private Instant revogadaEm;

    protected Concessao() {
    }

    public Concessao(UUID contaId, String origem, Instant inicio, Instant fim, UUID concedidaPor, String motivo) {
        this.id = UUID.randomUUID();
        this.contaId = contaId;
        this.origem = origem;
        this.inicio = inicio;
        this.fim = fim;
        this.concedidaPor = concedidaPor;
        this.motivo = motivo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContaId() {
        return contaId;
    }

    public String getOrigem() {
        return origem;
    }

    public Instant getFim() {
        return fim;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getRevogadaEm() {
        return revogadaEm;
    }

    public boolean vigente(Instant agora) {
        return revogadaEm == null && !inicio.isAfter(agora) && (fim == null || fim.isAfter(agora));
    }

    public void revogar(Instant agora) {
        if (revogadaEm == null) {
            revogadaEm = agora;
        }
    }
}
