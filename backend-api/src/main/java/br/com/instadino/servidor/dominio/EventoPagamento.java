package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Registro de evento de pagamento já processado; a chave primária é o id do provedor e garante a idempotência.
 * Implementa {@link Persistable} para que {@code save} sempre faça INSERT: com id atribuído, o Spring Data usaria
 * {@code merge}, que atualiza em silêncio uma linha já confirmada por outra transação e deixaria o mesmo evento
 * ser processado duas vezes.
 */
@Entity
@Table(name = "evento_pagamento")
public class EventoPagamento implements Persistable<String> {

    @Id
    private String id;
    @Column(nullable = false)
    private String tipo;
    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm;
    @Column(name = "criado_no_provedor_em", nullable = false)
    private Instant criadoNoProvedorEm;
    @Column(name = "pedido_id")
    private UUID pedidoId;
    @Column(nullable = false)
    private String resultado;
    @Transient
    private boolean novo = true;

    protected EventoPagamento() {
    }

    public EventoPagamento(String id, String tipo, Instant recebidoEm, Instant criadoNoProvedorEm, UUID pedidoId, String resultado) {
        this.id = id;
        this.tipo = tipo;
        this.recebidoEm = recebidoEm;
        this.criadoNoProvedorEm = criadoNoProvedorEm;
        this.pedidoId = pedidoId;
        this.resultado = resultado;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    @PostLoad
    @PostPersist
    void marcarComoPersistido() {
        this.novo = false;
    }

    public String getResultado() {
        return resultado;
    }

    public void definirResultado(String resultado) {
        this.resultado = resultado;
    }
}
