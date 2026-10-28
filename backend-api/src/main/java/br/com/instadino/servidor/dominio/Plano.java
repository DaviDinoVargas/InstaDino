package br.com.instadino.servidor.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "plano")
public class Plano {

    @Id
    private String id;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private String descricao;
    @Column(nullable = false)
    private String periodicidade;
    @Column(name = "preco_centavos", nullable = false)
    private long precoCentavos;
    @Column(nullable = false)
    private String moeda;
    @Column(nullable = false)
    private boolean ativo;

    protected Plano() {
    }

    public Plano(String id, String nome, String descricao, String periodicidade, long precoCentavos, String moeda, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.periodicidade = periodicidade;
        this.precoCentavos = precoCentavos;
        this.moeda = moeda;
        this.ativo = ativo;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getPeriodicidade() {
        return periodicidade;
    }

    public long getPrecoCentavos() {
        return precoCentavos;
    }

    public String getMoeda() {
        return moeda;
    }

    public boolean isAtivo() {
        return ativo;
    }

    /** Duração de um período de cobrança em dias. */
    public int diasDoPeriodo() {
        return "ANUAL".equals(periodicidade) ? 365 : 30;
    }
}
