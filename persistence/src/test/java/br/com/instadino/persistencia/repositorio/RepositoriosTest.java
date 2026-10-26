package br.com.instadino.persistencia.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.Chaves;
import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoriosTest {

    @TempDir
    Path pasta;

    BancoLocal banco;

    @BeforeEach
    void abrir() {
        banco = BancoLocal.abrir(pasta.resolve("r.db"), Chaves.A, CarregadorDeMigracoes.doClasspath());
    }

    @AfterEach
    void fechar() {
        banco.close();
    }

    @Test
    void preferenciasSaoGravadasEAtualizadas() {
        var repo = new RepositorioDePreferencias(banco);
        assertThat(repo.obter("tema")).isEmpty();
        repo.definir("tema", "escuro");
        repo.definir("tema", "claro");
        assertThat(repo.obter("tema")).contains("claro");
    }

    @Test
    void semConsentimentoOModuloDeMensagensPermaneceBloqueadoMesmoNoPremium() {
        var consentimentos = new RepositorioDeConsentimentos(banco);
        var premium = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);
        var politica = new PoliticaDeAcesso(() -> premium, consentimentos);

        assertThat(consentimentos.concedido(Funcionalidade.ARQUIVO_DE_MENSAGENS)).isFalse();
        assertThat(politica.permite(Funcionalidade.ARQUIVO_DE_MENSAGENS)).isFalse();

        consentimentos.conceder(Funcionalidade.ARQUIVO_DE_MENSAGENS);
        assertThat(politica.permite(Funcionalidade.ARQUIVO_DE_MENSAGENS)).isTrue();

        consentimentos.revogar(Funcionalidade.ARQUIVO_DE_MENSAGENS);
        assertThat(politica.permite(Funcionalidade.ARQUIVO_DE_MENSAGENS)).isFalse();
    }

    @Test
    void consentimentoSoPodeSerConcedidoAFuncionalidadeSensivel() {
        var consentimentos = new RepositorioDeConsentimentos(banco);
        assertThatThrownBy(() -> consentimentos.conceder(Funcionalidade.RELATORIO_PDF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consentimentoPersisteEntreExecucoes() {
        new RepositorioDeConsentimentos(banco).conceder(Funcionalidade.ARQUIVO_DE_MENSAGENS);
        banco.close();
        abrir();
        assertThat(new RepositorioDeConsentimentos(banco).concedido(Funcionalidade.ARQUIVO_DE_MENSAGENS)).isTrue();
    }

    @Test
    void eventosDeSegurancaValidamTipoELimitamDetalhe() {
        var repo = new RepositorioDeEventosDeSeguranca(banco);
        repo.registrar("IMPORTACAO_CONCLUIDA", "x".repeat(1000));
        repo.registrar("DADOS_EXCLUIDOS", null);
        var eventos = repo.recentes(10);
        assertThat(eventos).hasSize(2);
        assertThat(eventos.get(0).tipo()).isEqualTo("DADOS_EXCLUIDOS");
        assertThat(eventos.get(1).detalhe()).hasSize(300);
        assertThatThrownBy(() -> repo.registrar("tipo'; DROP TABLE x;--", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
