package br.com.instadino.servidor.dominio;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Interfaces de acesso a dados. Todas as consultas são parametrizadas pelo Spring Data. */
public final class Repositorios {

    private Repositorios() {
    }

    public interface RepositorioDeContas extends JpaRepository<Conta, UUID> {
        Optional<Conta> findByEmailNormalizado(String emailNormalizado);

        boolean existsByEmailNormalizado(String emailNormalizado);
    }

    public interface RepositorioDeSessoes extends JpaRepository<Sessao, UUID> {
        Optional<Sessao> findByHashToken(String hashToken);

        List<Sessao> findByContaIdOrderByCriadaEmDesc(UUID contaId);

        Optional<Sessao> findByIdAndContaId(UUID id, UUID contaId);

        @Modifying
        @Query("update Sessao s set s.revogadaEm = :agora where s.contaId = :contaId and s.revogadaEm is null")
        int revogarTodasDaConta(@Param("contaId") UUID contaId, @Param("agora") Instant agora);
    }

    public interface RepositorioDeTokens extends JpaRepository<TokenDeUsoUnico, UUID> {
        Optional<TokenDeUsoUnico> findByHashToken(String hashToken);

        /** Consumo atômico: só devolve 1 se o token existe, é da finalidade, não foi usado e não expirou. */
        @Modifying
        @Query("update TokenDeUsoUnico t set t.usadoEm = :agora where t.hashToken = :hash and t.finalidade = :finalidade "
                + "and t.usadoEm is null and t.expiraEm > :agora")
        int consumir(@Param("hash") String hash, @Param("finalidade") TokenDeUsoUnico.Finalidade finalidade, @Param("agora") Instant agora);
    }

    public interface RepositorioDeDispositivos extends JpaRepository<Dispositivo, UUID> {
        Optional<Dispositivo> findByContaIdAndIdentificador(UUID contaId, String identificador);

        List<Dispositivo> findByContaIdAndRevogadoEmIsNull(UUID contaId);

        Optional<Dispositivo> findByIdAndContaId(UUID id, UUID contaId);
    }

    public interface RepositorioDePlanos extends JpaRepository<Plano, String> {
        List<Plano> findByAtivoTrueOrderByPrecoCentavosAsc();
    }

    public interface RepositorioDePedidos extends JpaRepository<Pedido, UUID> {
        List<Pedido> findByContaIdOrderByCriadoEmDesc(UUID contaId);

        Optional<Pedido> findByIdAndContaId(UUID id, UUID contaId);
    }

    public interface RepositorioDeAssinaturas extends JpaRepository<Assinatura, UUID> {
        Optional<Assinatura> findByContaId(UUID contaId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select a from Assinatura a where a.contaId = :contaId")
        Optional<Assinatura> bloquearPorConta(@Param("contaId") UUID contaId);
    }

    public interface RepositorioDeConcessoes extends JpaRepository<Concessao, UUID> {
        List<Concessao> findByContaId(UUID contaId);
    }

    public interface RepositorioDeLicencas extends JpaRepository<LicencaEmitida, UUID> {
        List<LicencaEmitida> findByContaIdAndRevogadaEmIsNull(UUID contaId);

        @Modifying
        @Query("update LicencaEmitida l set l.revogadaEm = :agora where l.contaId = :contaId and l.revogadaEm is null")
        int revogarTodasDaConta(@Param("contaId") UUID contaId, @Param("agora") Instant agora);
    }

    public interface RepositorioDeEventosDePagamento extends JpaRepository<EventoPagamento, String> {
    }

    public interface RepositorioDeAuditoria extends org.springframework.data.repository.Repository<EventoDeAuditoria, Long> {
        // Somente inclusão e leitura: não há métodos de alteração ou exclusão.
        EventoDeAuditoria save(EventoDeAuditoria evento);

        List<EventoDeAuditoria> findTop200ByOrderByIdDesc();

        long count();
    }
}
