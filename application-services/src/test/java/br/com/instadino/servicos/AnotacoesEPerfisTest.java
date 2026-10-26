package br.com.instadino.servicos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.ConfiguracaoDeImportacao;
import br.com.instadino.importacao.OuvinteDeProgresso;
import br.com.instadino.nucleo.acesso.AcessoNegadoException;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorSomenteDesenvolvimento;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnotacoesEPerfisTest {

    private static final EstadoDaLicenca PREMIUM = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);

    @TempDir
    Path pasta;

    private final AtomicReference<EstadoDaLicenca> licenca = new AtomicReference<>(PREMIUM);
    private ContextoLocal ctx;

    private ContextoLocal abrir() {
        ctx = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca::get);
        return ctx;
    }

    @AfterEach
    void fechar() {
        if (ctx != null) {
            ctx.close();
        }
    }

    private void importarExemplo() {
        Path zip = ConstrutorDeExportacao.exemplo().escreverEm(pasta.resolve("e-" + System.nanoTime() + ".zip"));
        var r = ctx.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        ctx.importacao().confirmar(r, ctx.sessao().perfilAtivo(), "e.zip", 1L);
    }

    // ---- política do plano ----

    @Test
    void freeNaoCriaFavoritosNemEtiquetasNemNotasMasPodeLer() {
        licenca.set(EstadoDaLicenca.GRATUITO);
        abrir();
        assertThatThrownBy(() -> ctx.anotacoes().favoritar("ana", true)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> ctx.anotacoes().definirNota("ana", "oi")).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> ctx.anotacoes().criarEtiqueta("x")).isInstanceOf(AcessoNegadoException.class);
        assertThat(ctx.anotacoes().doPerfilAtivo()).isEmpty();
        assertThat(ctx.anotacoes().etiquetas()).isEmpty();
    }

    @Test
    void perderOPremiumNaoPrendeOUsuarioAosProprioDados() {
        abrir();
        var etiqueta = ctx.anotacoes().criarEtiqueta("Amigos");
        ctx.anotacoes().favoritar("ana", true);
        ctx.anotacoes().definirNota("ana", "nota");
        ctx.anotacoes().aplicarEtiqueta("ana", etiqueta.id());

        licenca.set(EstadoDaLicenca.GRATUITO);
        // ler continua livre
        assertThat(ctx.anotacoes().doPerfilAtivo().get("ana").favorito()).isTrue();
        // criar/alterar não
        assertThatThrownBy(() -> ctx.anotacoes().favoritar("bia", true)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> ctx.anotacoes().aplicarEtiqueta("bia", etiqueta.id())).isInstanceOf(AcessoNegadoException.class);
        // remover é sempre permitido
        ctx.anotacoes().favoritar("ana", false);
        ctx.anotacoes().definirNota("ana", "");
        ctx.anotacoes().removerEtiqueta("ana", etiqueta.id());
        ctx.anotacoes().excluirEtiqueta(etiqueta.id());
        assertThat(ctx.anotacoes().doPerfilAtivo()).isEmpty();
        assertThat(ctx.anotacoes().etiquetas()).isEmpty();
    }

    // ---- filtros nas listas ----

    @Test
    void filtrosPorFavoritaEEtiquetaSaoPremiumEFuncionam() {
        abrir();
        importarExemplo();
        var todos = ctx.relacionamentos().todas(VisaoDeRelacionamento.SEGUIDORES);
        String primeiro = todos.get(0).usuario();
        String segundo = todos.get(1).usuario();
        var etiqueta = ctx.anotacoes().criarEtiqueta("Importantes");
        ctx.anotacoes().favoritar(primeiro, true);
        ctx.anotacoes().aplicarEtiqueta(segundo, etiqueta.id());

        var favoritas = ctx.relacionamentos().consultar(ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.SEGUIDORES).somenteFavoritas(true));
        assertThat(favoritas.itens()).extracting(ContaListada::usuario).containsExactly(primeiro);
        var etiquetadas = ctx.relacionamentos().consultar(ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.SEGUIDORES).comEtiqueta(etiqueta.id()));
        assertThat(etiquetadas.itens()).extracting(ContaListada::usuario).containsExactly(segundo);

        licenca.set(EstadoDaLicenca.GRATUITO);
        assertThatThrownBy(() -> ctx.relacionamentos().consultar(ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.SEGUIDORES).somenteFavoritas(true)))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> ctx.relacionamentos().consultar(ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.SEGUIDORES).comEtiqueta(etiqueta.id())))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void anotacoesSobrevivemANovasImportacoesEExclusaoDeImportacoes() {
        abrir();
        importarExemplo();
        ctx.anotacoes().favoritar("ana", true);
        importarExemplo();
        ctx.historico().listar().forEach(r -> ctx.historico().excluir(r.id()));
        assertThat(ctx.anotacoes().doPerfilAtivo()).containsKey("ana");
    }

    // ---- perfis ----

    @Test
    void freeTemUmPerfilEPremiumCriaMais() {
        licenca.set(EstadoDaLicenca.GRATUITO);
        abrir();
        assertThat(ctx.servicoDePerfis().listar()).hasSize(1);
        assertThatThrownBy(() -> ctx.servicoDePerfis().criar("Segundo")).isInstanceOf(AcessoNegadoException.class).hasMessageContaining("Premium");
        licenca.set(PREMIUM);
        var novo = ctx.servicoDePerfis().criar("Segundo");
        assertThat(ctx.servicoDePerfis().ativo().id()).isEqualTo(novo.id());
        assertThat(ctx.servicoDePerfis().listar()).hasSize(2);
    }

    @Test
    void dadosEAnotacoesFicamIsoladosPorPerfil() {
        abrir();
        long principal = ctx.servicoDePerfis().ativo().id();
        importarExemplo();
        ctx.anotacoes().favoritar("ana", true);
        ctx.servicoDePerfis().criar("Outra conta");
        assertThat(ctx.relacionamentos().painel()).isEmpty();
        assertThat(ctx.anotacoes().doPerfilAtivo()).isEmpty();
        ctx.servicoDePerfis().selecionar(principal);
        assertThat(ctx.relacionamentos().painel()).isPresent();
        assertThat(ctx.anotacoes().doPerfilAtivo()).containsKey("ana");
    }

    @Test
    void semPremiumOsPerfisExistentesContinuamAcessiveis() {
        abrir();
        long principal = ctx.servicoDePerfis().ativo().id();
        var segundo = ctx.servicoDePerfis().criar("Segundo");
        licenca.set(EstadoDaLicenca.GRATUITO);
        ctx.servicoDePerfis().selecionar(principal);
        ctx.servicoDePerfis().selecionar(segundo.id());
        ctx.servicoDePerfis().renomear(segundo.id(), "Renomeado");
        assertThat(ctx.servicoDePerfis().ativo().nome()).isEqualTo("Renomeado");
    }

    @Test
    void excluirPerfilApagaOsDadosDeleEMudaOAtivoENaoDeixaExcluirOUltimo() {
        abrir();
        long principal = ctx.servicoDePerfis().ativo().id();
        var segundo = ctx.servicoDePerfis().criar("Segundo");
        ctx.anotacoes().favoritar("zeca", true);
        ctx.servicoDePerfis().excluir(segundo.id());
        assertThat(ctx.servicoDePerfis().ativo().id()).isEqualTo(principal);
        assertThat(ctx.servicoDePerfis().listar()).hasSize(1);
        assertThatThrownBy(() -> ctx.servicoDePerfis().excluir(principal)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ctx.servicoDePerfis().selecionar(segundo.id())).isInstanceOf(IllegalArgumentException.class);
        assertThat(ctx.dados().eventosDeSeguranca(10)).extracting(e -> e.tipo()).contains("PERFIL_CRIADO", "PERFIL_EXCLUIDO");
    }
}
