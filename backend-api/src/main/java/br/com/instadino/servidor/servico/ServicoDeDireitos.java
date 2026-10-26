package br.com.instadino.servidor.servico;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.contratos.pagamento.EstadoDaAssinatura;
import br.com.instadino.servidor.dominio.Assinatura;
import br.com.instadino.servidor.dominio.Concessao;
import br.com.instadino.servidor.dominio.Conta;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeAssinaturas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeConcessoes;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeContas;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decide, NO SERVIDOR, se uma conta tem direito ao Premium. O direito vem de uma destas fontes, em ordem:
 * papel MASTER, concessão administrativa vigente ou assinatura paga com período em vigor. Nada que o cliente
 * informe participa da decisão.
 */
@Service
public class ServicoDeDireitos {

    public static final String ORIGEM_MASTER = "PAPEL_MASTER";
    public static final String ORIGEM_CONCESSAO = "CONCESSAO_ADMINISTRATIVA";
    public static final String ORIGEM_ASSINATURA = "ASSINATURA";

    /** Resultado interno; {@code fim} é nulo quando o direito não tem prazo. */
    public record Direito(boolean premium, String origem, Instant fim, String situacao) {
    }

    private final RepositorioDeContas contas;
    private final RepositorioDeConcessoes concessoes;
    private final RepositorioDeAssinaturas assinaturas;
    private final Clock relogio;

    ServicoDeDireitos(RepositorioDeContas contas, RepositorioDeConcessoes concessoes, RepositorioDeAssinaturas assinaturas, Clock relogio) {
        this.contas = contas;
        this.concessoes = concessoes;
        this.assinaturas = assinaturas;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Direito direitoDa(UUID contaId) {
        Instant agora = relogio.instant();
        Conta conta = contas.findById(contaId).filter(Conta::isAtiva).orElse(null);
        if (conta == null) {
            return new Direito(false, null, null, "CONTA_INATIVA");
        }
        if (conta.getPapel() == Papel.MASTER) {
            return new Direito(true, ORIGEM_MASTER, null, "ATIVA");
        }
        Optional<Concessao> concessao = concessoes.findByContaId(contaId).stream().filter(c -> c.vigente(agora))
                .max(Comparator.comparing(c -> c.getFim() == null ? Instant.MAX : c.getFim()));
        if (concessao.isPresent()) {
            return new Direito(true, ORIGEM_CONCESSAO, concessao.get().getFim(), "ATIVA");
        }
        Optional<Assinatura> assinatura = assinaturas.findByContaId(contaId);
        if (assinatura.isPresent()) {
            Assinatura a = assinatura.get();
            boolean daAcesso = (a.getSituacao() == Assinatura.Situacao.ATIVA || a.getSituacao() == Assinatura.Situacao.CANCELADA
                    || a.getSituacao() == Assinatura.Situacao.PAGAMENTO_PENDENTE)
                    && a.getPeriodoFim() != null && a.getPeriodoFim().isAfter(agora);
            if (daAcesso) {
                return new Direito(true, ORIGEM_ASSINATURA, a.getPeriodoFim(), a.getSituacao().name());
            }
            return new Direito(false, null, null, a.getSituacao().name());
        }
        return new Direito(false, null, null, "SEM_ASSINATURA");
    }

    @Transactional(readOnly = true)
    public EstadoDaAssinatura estadoDa(UUID contaId) {
        Direito d = direitoDa(contaId);
        String plano = assinaturas.findByContaId(contaId).map(Assinatura::getPlanoId).orElse(null);
        return new EstadoDaAssinatura(d.situacao(), plano, d.fim(), d.premium(), d.origem());
    }
}
