package br.com.instadino.servidor.servico;

import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeContas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeSessoes;
import br.com.instadino.servidor.seguranca.CriptoDeTokens;
import br.com.instadino.servidor.seguranca.PrincipalAutenticado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autenticação de requisições por token de sessão opaco. */
@Service
public class ServicoDeSessoes {

    private static final Duration INTERVALO_DE_ATUALIZACAO = Duration.ofMinutes(5);

    private final RepositorioDeSessoes sessoes;
    private final RepositorioDeContas contas;
    private final Clock relogio;

    ServicoDeSessoes(RepositorioDeSessoes sessoes, RepositorioDeContas contas, Clock relogio) {
        this.sessoes = sessoes;
        this.contas = contas;
        this.relogio = relogio;
    }

    @Transactional
    public Optional<PrincipalAutenticado> autenticar(String tokenBruto) {
        if (tokenBruto == null || tokenBruto.length() < 20 || tokenBruto.length() > 200) {
            return Optional.empty();
        }
        Instant agora = relogio.instant();
        return sessoes.findByHashToken(CriptoDeTokens.hash(tokenBruto)).filter(s -> s.valida(agora)).flatMap(s ->
                contas.findById(s.getContaId()).filter(c -> c.isAtiva() && c.isEmailVerificado()).map(c -> {
                    if (s.getUltimoUso().plus(INTERVALO_DE_ATUALIZACAO).isBefore(agora)) {
                        s.tocar(agora);
                    }
                    return new PrincipalAutenticado(c.getId(), s.getId(), c.getPapel());
                }));
    }
}
