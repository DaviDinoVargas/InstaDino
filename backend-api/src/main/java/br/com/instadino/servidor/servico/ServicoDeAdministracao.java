package br.com.instadino.servidor.servico;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.servidor.dominio.Concessao;
import br.com.instadino.servidor.dominio.Conta;
import br.com.instadino.servidor.dominio.EventoDeAuditoria;
import br.com.instadino.servidor.dominio.Plano;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeAuditoria;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeConcessoes;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeContas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeLicencas;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDePlanos;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeSessoes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operações restritas ao papel MASTER. Cada ação é auditada com o ator. */
@Service
public class ServicoDeAdministracao {

    public record ContaAdministrativa(UUID id, String email, String nome, Papel papel, boolean ativa, boolean emailVerificado,
                                      boolean premium, String origemDoDireito) {
    }

    private final RepositorioDeContas contas;
    private final RepositorioDeConcessoes concessoes;
    private final RepositorioDeSessoes sessoes;
    private final RepositorioDeLicencas licencas;
    private final RepositorioDePlanos planos;
    private final RepositorioDeAuditoria auditoriaRepositorio;
    private final ServicoDeDireitos direitos;
    private final ServicoDeAuditoria auditoria;
    private final Clock relogio;

    ServicoDeAdministracao(RepositorioDeContas contas, RepositorioDeConcessoes concessoes, RepositorioDeSessoes sessoes,
                           RepositorioDeLicencas licencas, RepositorioDePlanos planos, RepositorioDeAuditoria auditoriaRepositorio,
                           ServicoDeDireitos direitos, ServicoDeAuditoria auditoria, Clock relogio) {
        this.contas = contas;
        this.concessoes = concessoes;
        this.sessoes = sessoes;
        this.licencas = licencas;
        this.planos = planos;
        this.auditoriaRepositorio = auditoriaRepositorio;
        this.direitos = direitos;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<ContaAdministrativa> listarContas() {
        return contas.findAll().stream().sorted(java.util.Comparator.comparing(Conta::getCriadaEm)).limit(500).map(c -> {
            var d = direitos.direitoDa(c.getId());
            return new ContaAdministrativa(c.getId(), c.getEmail(), c.getNome(), c.getPapel(), c.isAtiva(), c.isEmailVerificado(),
                    d.premium(), d.origem());
        }).toList();
    }

    /** Concede Premium por {@code dias} (nulo = sem prazo). O motivo é obrigatório e fica na auditoria. */
    @Transactional
    public UUID conceder(UUID atorId, UUID contaId, Integer dias, String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.length() > 200) {
            throw new ErroDeNegocio(HttpStatus.BAD_REQUEST, "MOTIVO_OBRIGATORIO", "Informe o motivo da concessão (até 200 caracteres).");
        }
        if (dias != null && (dias < 1 || dias > 3650)) {
            throw new ErroDeNegocio(HttpStatus.BAD_REQUEST, "PRAZO_INVALIDO", "O prazo deve ficar entre 1 e 3650 dias.");
        }
        contas.findById(contaId).orElseThrow(() -> new ErroDeNegocio(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Conta não encontrada."));
        Instant agora = relogio.instant();
        Concessao c = concessoes.save(new Concessao(contaId, ServicoDeDireitos.ORIGEM_CONCESSAO, agora,
                dias == null ? null : agora.plus(Duration.ofDays(dias)), atorId, motivo.strip()));
        auditoria.registrar(atorId, "MASTER", "CONCESSAO_CRIADA", contaId.toString(), "dias=" + dias + "; " + motivo.strip());
        return c.getId();
    }

    @Transactional
    public void revogarConcessao(UUID atorId, UUID concessaoId) {
        Concessao c = concessoes.findById(concessaoId)
                .orElseThrow(() -> new ErroDeNegocio(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Concessão não encontrada."));
        c.revogar(relogio.instant());
        auditoria.registrar(atorId, "MASTER", "CONCESSAO_REVOGADA", c.getContaId().toString(), concessaoId.toString());
    }

    @Transactional
    public void revogarSessoes(UUID atorId, UUID contaId) {
        int n = sessoes.revogarTodasDaConta(contaId, relogio.instant());
        auditoria.registrar(atorId, "MASTER", "SESSOES_REVOGADAS", contaId.toString(), "quantidade=" + n);
    }

    @Transactional
    public void revogarLicencas(UUID atorId, UUID contaId) {
        int n = licencas.revogarTodasDaConta(contaId, relogio.instant());
        auditoria.registrar(atorId, "MASTER", "LICENCAS_REVOGADAS", contaId.toString(), "quantidade=" + n);
    }

    @Transactional
    public void desativarConta(UUID atorId, UUID contaId) {
        Conta c = contas.findById(contaId).orElseThrow(() -> new ErroDeNegocio(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Conta não encontrada."));
        if (c.getId().equals(atorId)) {
            throw new ErroDeNegocio(HttpStatus.CONFLICT, "OPERACAO_NAO_PERMITIDA", "Você não pode desativar a própria conta.");
        }
        Instant agora = relogio.instant();
        c.desativar(agora);
        sessoes.revogarTodasDaConta(contaId, agora);
        licencas.revogarTodasDaConta(contaId, agora);
        auditoria.registrar(atorId, "MASTER", "CONTA_DESATIVADA", contaId.toString(), null);
    }

    @Transactional
    public void criarPlano(UUID atorId, String id, String nome, String descricao, String periodicidade, long precoEmCentavos) {
        if (id == null || !id.matches("[a-z0-9-]{3,40}") || nome == null || nome.isBlank() || descricao == null
                || !("MENSAL".equals(periodicidade) || "ANUAL".equals(periodicidade)) || precoEmCentavos < 0 || precoEmCentavos > 10_000_000) {
            throw new ErroDeNegocio(HttpStatus.BAD_REQUEST, "PLANO_INVALIDO", "Dados do plano inválidos.");
        }
        planos.save(new Plano(id, nome.strip(), descricao.strip(), periodicidade, precoEmCentavos, "BRL", true));
        auditoria.registrar(atorId, "MASTER", "PLANO_SALVO", id, "preco=" + precoEmCentavos);
    }

    @Transactional(readOnly = true)
    public List<EventoDeAuditoria> auditoriaRecente() {
        return auditoriaRepositorio.findTop200ByOrderByIdDesc();
    }
}
