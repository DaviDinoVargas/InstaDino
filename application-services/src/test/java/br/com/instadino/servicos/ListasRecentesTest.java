package br.com.instadino.servicos;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.ConfiguracaoDeImportacao;
import br.com.instadino.importacao.OuvinteDeProgresso;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorSomenteDesenvolvimento;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListasRecentesTest {

    @TempDir
    Path pasta;

    private ContextoLocal ctx;

    @AfterEach
    void fechar() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void listasRecentesSaoPersistidasEExibidasNoPlanoFree() {
        ctx = ContextoLocal.abrir(pasta.resolve("d"), new ProtetorSomenteDesenvolvimento(), () -> EstadoDaLicenca.GRATUITO);
        var c = new ConstrutorDeExportacao().seguidores("ana").seguindo("ana");
        c.arquivo("connections/followers_and_following/recently_unfollowed_profiles.json",
                "{\"relationships_unfollowed_users\":[{\"timestamp\":1700000000,\"label_values\":[{\"label\":\"URL\",\"value\":\"https://www.instagram.com/bia\"}]}]}");
        Path zip = c.escreverEm(pasta.resolve("e.zip"));
        var r = ctx.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        ctx.importacao().confirmar(r, ctx.sessao().perfilAtivo(), "e.zip", 1L);
        assertThat(ctx.relacionamentos().todas(VisaoDeRelacionamento.DEIXEI_DE_SEGUIR)).extracting(ContaListada::usuario).containsExactly("bia");
        assertThat(ctx.relacionamentos().todas(VisaoDeRelacionamento.SOLICITACOES_RECEBIDAS)).isEmpty();
        // o painel de seguidores não é afetado
        assertThat(ctx.relacionamentos().painel().orElseThrow().seguidores()).isEqualTo(1);
    }
}
