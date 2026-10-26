package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "token_uso_unico")
public class TokenDeUsoUnico {

    public enum Finalidade { VERIFICACAO_EMAIL, RECUPERACAO_SENHA }

    @Id
    private UUID id;
    @Column(name = "conta_id", nullable = false)
    private UUID contaId;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Finalidade finalidade;
    @Column(name = "hash_token", nullable = false)
    private String hashToken;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;
    @Column(name = "usado_em")
    private Instant usadoEm;

    protected TokenDeUsoUnico() {
    }

    public TokenDeUsoUnico(UUID contaId, Finalidade finalidade, String hashToken, Instant agora, Instant expiraEm) {
        this.id = UUID.randomUUID();
        this.contaId = contaId;
        this.finalidade = finalidade;
        this.hashToken = hashToken;
        this.criadoEm = agora;
        this.expiraEm = expiraEm;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContaId() {
        return contaId;
    }

    public Finalidade getFinalidade() {
        return finalidade;
    }
}
