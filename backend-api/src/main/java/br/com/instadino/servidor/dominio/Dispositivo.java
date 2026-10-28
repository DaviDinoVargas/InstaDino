package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispositivo")
public class Dispositivo {

    @Id
    private UUID id;
    @Column(name = "conta_id", nullable = false)
    private UUID contaId;
    @Column(nullable = false)
    private String identificador;
    private String nome;
    @Column(name = "ativado_em", nullable = false)
    private Instant ativadoEm;
    @Column(name = "revogado_em")
    private Instant revogadoEm;

    protected Dispositivo() {
    }

    public Dispositivo(UUID contaId, String identificador, String nome, Instant agora) {
        this.id = UUID.randomUUID();
        this.contaId = contaId;
        this.identificador = identificador;
        this.nome = nome;
        this.ativadoEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContaId() {
        return contaId;
    }

    public String getIdentificador() {
        return identificador;
    }

    public String getNome() {
        return nome;
    }

    public Instant getAtivadoEm() {
        return ativadoEm;
    }

    public boolean ativo() {
        return revogadoEm == null;
    }

    public void revogar(Instant agora) {
        if (revogadoEm == null) {
            revogadoEm = agora;
        }
    }

    public void reativar(String novoNome, Instant agora) {
        this.revogadoEm = null;
        this.nome = novoNome;
        this.ativadoEm = agora;
    }
}
