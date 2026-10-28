package br.com.instadino.servicos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.importacao.ConfiguracaoDeImportacao;
import br.com.instadino.importacao.OuvinteDeProgresso;
import br.com.instadino.nucleo.acesso.AcessoNegadoException;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorSomenteDesenvolvimento;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExploradorEPaineisTest {

    private static final EstadoDaLicenca PREMIUM = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);

    @TempDir
    Path pasta;

    private final AtomicReference<EstadoDaLicenca> licenca = new AtomicReference<>(PREMIUM);
    private ContextoLocal ctx;

    @BeforeEach
    void abrir() {
        ctx = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca::get);
    }

    @AfterEach
    void fechar() {
        ctx.close();
    }

    private void importar() {
        var c = new ConstrutorDeExportacao().seguidores("ana", "bia").seguindo("ana")
                .arquivo("conta/messages/inbox/a_1/message_1.json", "{\"participants\":[{\"name\":\"E\"},{\"name\":\"A\"}],\"messages\":[{\"sender_name\":\"E\",\"timestamp_ms\":1700000000000}],\"title\":\"A\"}")
                .arquivo("conta/personal_information/personal_information.json", "{\"profile_user\":[{\"string_map_data\":{}}]}")
                .arquivo("conta/estranho/arquivo_qualquer.json", "{}");
        Path zip = c.escreverEm(pasta.resolve("e.zip"));
        var r = ctx.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        ctx.importacao().confirmar(r, ctx.sessao().perfilAtivo(), "e.zip", 1L);
    }

    @Test
    void exploradorMostraSituacaoDeCadaCategoriaSemLerConteudo() {
        importar();
        var id = ctx.explorador().importacoes().get(0).id();
        var linhas = ctx.explorador().categorias(id);
        var porCategoria = linhas.stream().collect(java.util.stream.Collectors.toMap(ServicoDeExplorador.LinhaDeCategoria::categoria, l -> l));
        assertThat(porCategoria.get(CategoriaDeDados.SEGUIDORES).situacao()).isEqualTo(ServicoDeExplorador.Situacao.LIDA);
        assertThat(porCategoria.get(CategoriaDeDados.SEGUIDORES).registros()).isEqualTo(2);
        assertThat(porCategoria.get(CategoriaDeDados.MENSAGENS).situacao()).as("sem consentimento").isEqualTo(ServicoDeExplorador.Situacao.SENSIVEL_NAO_LIDA);
        assertThat(porCategoria.get(CategoriaDeDados.PERFIL).situacao()).isEqualTo(ServicoDeExplorador.Situacao.SENSIVEL_NAO_LIDA);
        assertThat(porCategoria.get(CategoriaDeDados.DESCONHECIDA).situacao()).isEqualTo(ServicoDeExplorador.Situacao.NAO_RECONHECIDA);
        assertThat(linhas.get(0).registros()).isGreaterThanOrEqualTo(linhas.get(linhas.size() - 1).registros());
    }

    @Test
    void exploradorEPremium() {
        licenca.set(EstadoDaLicenca.GRATUITO);
        assertThatThrownBy(() -> ctx.explorador().importacoes()).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void paineisOcultamBlocosSoNoPremiumEIgnoramIdentificadoresInvalidos() {
        assertThat(ctx.paineis().ocultos()).isEmpty();
        ctx.paineis().definirOcultos(Set.of("composicao", "atalhos", "inexistente"));
        assertThat(ctx.paineis().ocultos()).containsExactly("atalhos", "composicao");
        assertThat(ctx.paineis().visivel("seguidores")).isTrue();
        assertThat(ctx.paineis().visivel("composicao")).isFalse();
        licenca.set(EstadoDaLicenca.GRATUITO);
        assertThat(ctx.paineis().ocultos()).as("sem Premium o painel volta ao completo").isEmpty();
        assertThatThrownBy(() -> ctx.paineis().definirOcultos(Set.of("atalhos"))).isInstanceOf(AcessoNegadoException.class);
    }
}
