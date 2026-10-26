package br.com.instadino.servidor.dominio;

import br.com.instadino.contratos.conta.Papel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conta")
public class Conta {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String email;
    @Column(name = "email_normalizado", nullable = false)
    private String emailNormalizado;
    @Column(nullable = false)
    private String nome;
    @Column(name = "hash_senha", nullable = false)
    private String hashSenha;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel papel;
    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado;
    @Column(nullable = false)
    private boolean ativa = true;
    @Column(name = "tentativas_falhas", nullable = false)
    private int tentativasFalhas;
    @Column(name = "bloqueada_ate")
    private Instant bloqueadaAte;
    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;
    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    protected Conta() {
    }

    public Conta(String email, String emailNormalizado, String nome, String hashSenha, Papel papel, boolean emailVerificado, Instant agora) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.emailNormalizado = emailNormalizado;
        this.nome = nome;
        this.hashSenha = hashSenha;
        this.papel = papel;
        this.emailVerificado = emailVerificado;
        this.criadaEm = agora;
        this.atualizadaEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalizado() {
        return emailNormalizado;
    }

    public String getNome() {
        return nome;
    }

    public String getHashSenha() {
        return hashSenha;
    }

    public void definirHashSenha(String novoHash, Instant agora) {
        this.hashSenha = novoHash;
        this.tentativasFalhas = 0;
        this.bloqueadaAte = null;
        this.atualizadaEm = agora;
    }

    public Papel getPapel() {
        return papel;
    }

    public boolean isEmailVerificado() {
        return emailVerificado;
    }

    public void verificarEmail(Instant agora) {
        this.emailVerificado = true;
        this.atualizadaEm = agora;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void desativar(Instant agora) {
        this.ativa = false;
        this.atualizadaEm = agora;
    }

    public int getTentativasFalhas() {
        return tentativasFalhas;
    }

    public Instant getBloqueadaAte() {
        return bloqueadaAte;
    }

    public boolean bloqueada(Instant agora) {
        return bloqueadaAte != null && bloqueadaAte.isAfter(agora);
    }

    public void registrarFalha(int limite, java.time.Duration baseDoBloqueio, Instant agora) {
        tentativasFalhas++;
        if (tentativasFalhas >= limite) {
            int excesso = Math.min(tentativasFalhas - limite, 6);
            bloqueadaAte = agora.plus(baseDoBloqueio.multipliedBy(1L << excesso));
        }
        atualizadaEm = agora;
    }

    public void registrarSucesso(Instant agora) {
        tentativasFalhas = 0;
        bloqueadaAte = null;
        atualizadaEm = agora;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
