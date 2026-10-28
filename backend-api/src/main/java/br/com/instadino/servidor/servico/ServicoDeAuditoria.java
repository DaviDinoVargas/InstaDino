package br.com.instadino.servidor.servico;

import br.com.instadino.servidor.dominio.EventoDeAuditoria;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeAuditoria;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registra eventos relevantes. O detalhe nunca deve conter senhas, tokens ou segredos. */
@Service
public class ServicoDeAuditoria {

    private static final int LIMITE_DO_DETALHE = 300;

    private final RepositorioDeAuditoria repositorio;
    private final Clock relogio;

    ServicoDeAuditoria(RepositorioDeAuditoria repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void registrar(UUID atorId, String atorTipo, String acao, String alvo, String detalhe) {
        String limitado = detalhe == null ? null : detalhe.length() > LIMITE_DO_DETALHE ? detalhe.substring(0, LIMITE_DO_DETALHE) : detalhe;
        repositorio.save(new EventoDeAuditoria(relogio.instant(), atorId, atorTipo, acao, alvo, limitado));
    }

    public void sistema(String acao, String alvo, String detalhe) {
        registrar(null, "SISTEMA", acao, alvo, detalhe);
    }

    public void conta(UUID contaId, String acao, String alvo, String detalhe) {
        registrar(contaId, "CONTA", acao, alvo, detalhe);
    }
}
