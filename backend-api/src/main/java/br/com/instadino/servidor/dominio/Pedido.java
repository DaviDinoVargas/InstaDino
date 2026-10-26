package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pedido")
public class Pedido {

    public enum Situacao { PENDENTE, PAGO, RECUSADO, REEMBOLSADO, EM_DISPUTA }

    @Id
    private UUID id;
    @Column(name = "conta_id", nullable = false)
    private UUID contaId;
    @Column(name = "plano_id", nullable = false)
    private String planoId;
    @Column(name = "valor_centavos", nullable = false)
    private long valorCentavos;
    @Column(nullable = false)
    private String moeda;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Situacao situacao;
    @Column(name = "referencia_provedor")
    private String referenciaProvedor;
    @Column(nullable = false)
    private boolean sandbox;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Pedido() {
    }

    public Pedido(UUID contaId, Plano plano, boolean sandbox, Instant agora) {
        this.id = UUID.randomUUID();
        this.contaId = contaId;
        this.planoId = plano.getId();
        this.valorCentavos = plano.getPrecoCentavos();
        this.moeda = plano.getMoeda();
        this.situacao = Situacao.PENDENTE;
        this.sandbox = sandbox;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
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

    public long getValorCentavos() {
        return valorCentavos;
    }

    public String getMoeda() {
        return moeda;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public void definirSituacao(Situacao nova, Instant agora) {
        this.situacao = nova;
        this.atualizadoEm = agora;
    }

    public String getReferenciaProvedor() {
        return referenciaProvedor;
    }

    public void definirReferencia(String referencia) {
        this.referenciaProvedor = referencia;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
