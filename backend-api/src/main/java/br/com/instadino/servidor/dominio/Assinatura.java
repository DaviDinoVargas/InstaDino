package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Situação comercial de uma conta, alterada somente por eventos de pagamento autenticados. */
@Entity
@Table(name = "assinatura")
public class Assinatura {

    public enum Situacao { ATIVA, PAGAMENTO_PENDENTE, CANCELADA, REVOGADA, SUSPENSA }

    @Id
    private UUID id;
    @Column(name = "conta_id", nullable = false)
    private UUID contaId;
    @Column(name = "plano_id", nullable = false)
    private String planoId;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Situacao situacao;
    @Column(name = "periodo_fim")
    private Instant periodoFim;
    @Column(name = "ultimo_evento_em")
    private Instant ultimoEventoEm;
    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;
    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    protected Assinatura() {
    }

    public Assinatura(UUID contaId, String planoId, Instant agora) {
        this.id = UUID.randomUUID();
        this.contaId = contaId;
        this.planoId = planoId;
        this.situacao = Situacao.PAGAMENTO_PENDENTE;
        this.criadaEm = agora;
        this.atualizadaEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContaId() {
        return contaId;
    }

    public String getPlanoId() {
        return planoId;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public Instant getPeriodoFim() {
        return periodoFim;
    }

    public Instant getUltimoEventoEm() {
        return ultimoEventoEm;
    }

    public void atualizar(Situacao situacao, String planoId, Instant periodoFim, Instant eventoEm, Instant agora) {
        this.situacao = situacao;
        this.planoId = planoId;
        this.periodoFim = periodoFim;
        this.ultimoEventoEm = eventoEm;
        this.atualizadaEm = agora;
    }
}
