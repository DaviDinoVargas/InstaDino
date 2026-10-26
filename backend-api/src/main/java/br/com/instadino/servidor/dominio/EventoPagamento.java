package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Registro de evento de pagamento já processado; a chave primária é o id do provedor e garante a idempotência. */
@Entity
@Table(name = "evento_pagamento")
public class EventoPagamento {

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

    public String getId() {
        return id;
    }

    public String getResultado() {
        return resultado;
    }

    public void definirResultado(String resultado) {
        this.resultado = resultado;
    }
}
