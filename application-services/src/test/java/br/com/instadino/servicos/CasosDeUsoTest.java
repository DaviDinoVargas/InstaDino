package br.com.instadino.servicos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.ConfiguracaoDeImportacao;
import br.com.instadino.importacao.OuvinteDeProgresso;
import br.com.instadino.importacao.ResultadoDaImportacao;
import br.com.instadino.nucleo.acesso.AcessoNegadoException;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import br.com.instadino.persistencia.seguranca.ProtetorSomenteDesenvolvimento;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CasosDeUsoTest {

    @TempDir
    Path pasta;

    private final AtomicReference<EstadoDaLicenca> licenca = new AtomicReference<>(EstadoDaLicenca.GRATUITO);
    private ContextoLocal ctx;

    private static final EstadoDaLicenca PREMIUM = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);

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

    private String importar(ConstrutorDeExportacao exportacao) {
        Path zip = exportacao.escreverEm(pasta.resolve("e-" + System.nanoTime() + ".zip"));
        ResultadoDaImportacao r = ctx.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        return ctx.importacao().confirmar(r, ctx.sessao().perfilAtivo(), zip.getFileName().toString(), 100L);
    }

    @Test
    void planoFreeImportaEMostraOPainelSemCadastro() {
        abrir();
        assertThat(ctx.relacionamentos().painel()).isEmpty();
        importar(ConstrutorDeExportacao.exemplo());
        var painel = ctx.relacionamentos().painel().orElseThrow();
        assertThat(painel.seguidores()).isEqualTo(4);
        assertThat(painel.seguindo()).isEqualTo(4);
        assertThat(painel.mutuos()).isEqualTo(2);
        assertThat(painel.naoSeguemDeVolta()).isEqualTo(2);
        assertThat(painel.voceNaoSegue()).isEqualTo(2);
        assertThat(painel.proporcao()).isEqualTo(1.0);
        assertThat(painel.comparacaoDisponivel()).isTrue();
        assertThat(painel.importacao().registrosValidos()).isEqualTo(8);
    }

    @Test
    void planoFreeMantemSomenteAUltimaImportacaoENaoComparaPeriodos() {
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        String segunda = importar(new ConstrutorDeExportacao().seguidores("ana", "zeca").seguindo("ana"));
        assertThat(ctx.historico().listar()).extracting(r -> r.id()).containsExactly(segunda);
        assertThatThrownBy(() -> ctx.historico().comparar(segunda, segunda, TipoDeRelacionamento.SEGUIDOR))
                .isInstanceOf(AcessoNegadoException.class).hasMessageContaining("Premium");
    }

    @Test
    void comparacaoManualDeListasEGratuita() {
        abrir();
        var d = ctx.historico().compararManualmente("ana\nBruno\n@carla\nhttps://www.instagram.com/diego\nlixo invalido!", "bruno\ncarla\neduardo");
        assertThat(d.somenteNoPrimeiro()).extracting(u -> u.valor()).containsExactly("ana", "diego");
        assertThat(d.somenteNoSegundo()).extracting(u -> u.valor()).containsExactly("eduardo");
        assertThat(d.emAmbos()).extracting(u -> u.valor()).containsExactly("bruno", "carla");
    }

    @Test
    void premiumGuardaHistoricoECompararPeriodos() {
        licenca.set(PREMIUM);
        abrir();
        String antes = importar(new ConstrutorDeExportacao().seguidores("ana", "bruno", "carla").seguindo("ana"));
        String depois = importar(new ConstrutorDeExportacao().seguidores("bruno", "carla", "diego").seguindo("ana"));
        assertThat(ctx.historico().listar()).hasSize(2);

        var c = ctx.historico().comparar(antes, depois, TipoDeRelacionamento.SEGUIDOR);
        assertThat(c.novas()).extracting(u -> u.valor()).containsExactly("diego");
        assertThat(c.ausentes()).extracting(u -> u.valor()).containsExactly("ana");
        assertThat(c.mantidas()).hasSize(2);
        assertThat(c.confiavel()).isTrue();
        // a ordem dos argumentos não altera o sentido da comparação
        var invertida = ctx.historico().comparar(depois, antes, TipoDeRelacionamento.SEGUIDOR);
        assertThat(invertida.novas()).extracting(u -> u.valor()).containsExactly("diego");
    }

    @Test
    void comparacaoComListaIncompletaNaoEConfiavel() {
        licenca.set(PREMIUM);
        abrir();
        String boa = importar(new ConstrutorDeExportacao().seguidores("ana", "bruno").seguindo("ana"));
        var truncada = new ConstrutorDeExportacao().seguindo("ana");
        truncada.arquivo("connections/followers_and_following/followers_1.json", "[{\"string_list_data\":[{\"value\":\"ana\"}]},{\"str");
        String ruim = importar(truncada);
        assertThat(ctx.historico().comparar(boa, ruim, TipoDeRelacionamento.SEGUIDOR).confiavel()).isFalse();
        assertThat(ctx.historico().comparar(boa, boa, TipoDeRelacionamento.SEGUIDOR).confiavel()).isTrue();
    }

    @Test
    void historicoPersisteEntreExecucoes() {
        licenca.set(PREMIUM);
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        ctx.close();
        abrir();
        var painel = ctx.relacionamentos().painel().orElseThrow();
        assertThat(painel.seguidores()).isEqualTo(4);
        assertThat(ctx.historico().listar()).hasSize(1);
    }

    @Test
    void aposPerderOPremiumOsDadosLocaisContinuamAcessiveis() {
        licenca.set(PREMIUM);
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        importar(ConstrutorDeExportacao.exemplo());
        licenca.set(new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.EXPIRADA));

        assertThat(ctx.relacionamentos().painel()).isPresent();
        assertThat(ctx.relacionamentos().consultar(ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.SEGUIDORES)).total()).isEqualTo(4);
        assertThat(ctx.historico().listar()).as("Free enxerga o histórico recente, sem perder dados").hasSize(1);
        // os dados continuam gravados e voltam quando a licença volta
        licenca.set(PREMIUM);
        assertThat(ctx.historico().listar()).hasSize(2);
    }

    @Test
    void consultasComBuscaOrdenacaoEPaginacao() {
        abrir();
        importar(new ConstrutorDeExportacao().seguidores("ana1", "ana2", "ana3", "bruno", "carla").seguindo("ana1"));
        var s = ctx.relacionamentos();
        var base = ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.SEGUIDORES);

        assertThat(s.consultar(base.comBusca("ANA")).total()).isEqualTo(3);
        var paginaUm = s.consultar(new ConsultaDeRelacionamentos(VisaoDeRelacionamento.SEGUIDORES, "", ConsultaDeRelacionamentos.Ordenacao.ALFABETICA_INVERSA, 0, 2, null, null, false));
        assertThat(paginaUm.itens()).extracting(ContaListada::usuario).containsExactly("carla", "bruno");
        assertThat(paginaUm.totalDePaginas()).isEqualTo(3);
        var paginaTres = s.consultar(new ConsultaDeRelacionamentos(VisaoDeRelacionamento.SEGUIDORES, "", ConsultaDeRelacionamentos.Ordenacao.ALFABETICA_INVERSA, 2, 2, null, null, false));
        assertThat(paginaTres.itens()).extracting(ContaListada::usuario).containsExactly("ana1");
        assertThat(s.consultar(base.naPagina(99)).itens()).isEmpty();
        assertThat(s.consultar(ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.NAO_SEGUEM_DE_VOLTA)).total()).isZero();
        assertThat(s.consultar(ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.VOCE_NAO_SEGUE)).total()).isEqualTo(4);
    }

    @Test
    void filtrosCompostosSaoPremium() {
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        var filtro = ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.SEGUIDORES).comPeriodo(Instant.EPOCH, Instant.now());
        assertThatThrownBy(() -> ctx.relacionamentos().consultar(filtro)).isInstanceOf(AcessoNegadoException.class);
        licenca.set(PREMIUM);
        assertThat(ctx.relacionamentos().consultar(filtro).total()).isEqualTo(4);
        var semData = ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.SEGUIDORES).comPeriodo(Instant.now().plusSeconds(86400L * 365 * 10), null);
        assertThat(ctx.relacionamentos().consultar(semData).total()).isZero();
    }

    @Test
    void exportacaoCsvBasicaEGratuitaEPersonalizadaEPremium() throws IOException {
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        var saida = new ByteArrayOutputStream();
        ctx.exportacao().exportarCsv(VisaoDeRelacionamento.NAO_SEGUEM_DE_VOLTA, saida);
        String csv = saida.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("eduardo;https://www.instagram.com/eduardo/").contains("fernanda;").contains("não prova");

        assertThatThrownBy(() -> ctx.exportacao().exportarCsvPersonalizado(
                ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.SEGUIDORES), Set.of(ServicoDeExportacao.Coluna.USUARIO), new ByteArrayOutputStream()))
                .isInstanceOf(AcessoNegadoException.class);

        licenca.set(PREMIUM);
        var personalizado = new ByteArrayOutputStream();
        ctx.exportacao().exportarCsvPersonalizado(ConsultaDeRelacionamentos.de(VisaoDeRelacionamento.SEGUIDORES).comBusca("a"),
                EnumSet.of(ServicoDeExportacao.Coluna.USUARIO), personalizado);
        String linhas = personalizado.toString(StandardCharsets.UTF_8);
        assertThat(linhas).isEqualTo("\uFEFFusuario\r\nana\r\ncarla\r\n");
    }

    @Test
    void importacaoComFalhaDePersistenciaPreservaOHistoricoAnterior() {
        licenca.set(PREMIUM);
        abrir();
        String anterior = importar(ConstrutorDeExportacao.exemplo());
        Path zip = ConstrutorDeExportacao.exemplo().escreverEm(pasta.resolve("novo.zip"));
        ResultadoDaImportacao r = ctx.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        assertThatThrownBy(() -> ctx.importacao().confirmar(r, 424242L, "x.zip", 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ctx.historico().listar()).extracting(x -> x.id()).containsExactly(anterior);
    }

    @Test
    void processarNaoGravaNadaAteConfirmar() {
        abrir();
        Path zip = ConstrutorDeExportacao.exemplo().escreverEm(pasta.resolve("a.zip"));
        ctx.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        assertThat(ctx.historico().listar()).isEmpty();
        assertThat(ctx.relacionamentos().painel()).isEmpty();
    }

    @Test
    void mensagensNaoSaoProcessadasSemPlanoPremiumEConsentimento() {
        abrir();
        var exportacao = ConstrutorDeExportacao.exemplo().comCategoriasDiversas();
        Path zip = exportacao.escreverEm(pasta.resolve("m.zip"));
        var pedirMensagens = new ConfiguracaoDeImportacao(Set.of(br.com.instadino.importacao.CategoriaDeDados.MENSAGENS));
        // Free + pedido explícito: a política ainda assim impede o processamento do módulo
        var r = ctx.importacao().processar(zip, pedirMensagens, new Cancelamento(), OuvinteDeProgresso.NENHUM);
        assertThat(r.avisos()).anyMatch(a -> a.contains("Mensagens") && a.contains("não foi autorizada"));
    }

    @Test
    void historicoDeOutroPerfilNaoEAcessivel() {
        licenca.set(PREMIUM);
        abrir();
        String id = importar(ConstrutorDeExportacao.exemplo());
        var outro = ctx.perfis().criar("Outro perfil", null);
        ctx.sessao().selecionarPerfil(outro.id());
        assertThat(ctx.historico().listar()).isEmpty();
        assertThat(ctx.relacionamentos().painel()).isEmpty();
        assertThatThrownBy(() -> ctx.historico().excluir(id)).isInstanceOf(java.util.NoSuchElementException.class);
        assertThatThrownBy(() -> ctx.historico().comparar(id, id, TipoDeRelacionamento.SEGUIDOR)).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void excluirImportacaoAtualizaOPainel() {
        licenca.set(PREMIUM);
        abrir();
        importar(new ConstrutorDeExportacao().seguidores("a").seguindo("b"));
        String segunda = importar(ConstrutorDeExportacao.exemplo());
        assertThat(ctx.relacionamentos().painel().orElseThrow().seguidores()).isEqualTo(4);
        ctx.historico().excluir(segunda);
        assertThat(ctx.relacionamentos().painel().orElseThrow().seguidores()).as("cache invalidado").isEqualTo(1);
    }

    @Test
    void exclusaoTotalRemoveBancoEChaveETornaOContextoInutilizavel() throws IOException {
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        Path dados = pasta.resolve("dados");
        assertThat(dados.resolve("instadino.db")).exists();
        assertThat(dados.resolve("chave-do-banco.protegida")).exists();
        ctx.dados().excluirTodosOsDados();
        assertThat(dados.resolve("instadino.db")).doesNotExist();
        assertThat(dados.resolve("chave-do-banco.protegida")).doesNotExist();
        assertThat(ctx.excluido()).isTrue();
        try (var arquivos = Files.list(dados)) {
            assertThat(arquivos.toList()).isEmpty();
        }
        // um novo contexto começa do zero, sem dados antigos
        abrir();
        assertThat(ctx.relacionamentos().painel()).isEmpty();
    }

    @Test
    void situacaoDaProtecaoInformaCifragemEIntegridade() {
        abrir();
        importar(ConstrutorDeExportacao.exemplo());
        var s = ctx.dados().situacao();
        assertThat(s.cifragemAtiva()).isTrue();
        assertThat(s.integridadeOk()).isTrue();
        assertThat(s.importacoes()).isEqualTo(1);
        assertThat(s.seguroParaProducao()).as("protetor de desenvolvimento nunca é seguro para produção").isFalse();
        assertThat(ctx.dados().eventosDeSeguranca(10)).extracting(e -> e.tipo()).contains("IMPORTACAO_CONCLUIDA");
    }

    @Test
    void importaMuitosSeguidoresViaZipComTempoRazoavel() {
        abrir();
        List<String> seguidores = ConstrutorDeExportacao.nomes(120_000, "seguidor");
        List<String> seguindo = ConstrutorDeExportacao.nomes(60_000, "seguidor");
        var c = new ConstrutorDeExportacao().seguidores(seguidores.toArray(String[]::new)).seguindo(seguindo.toArray(String[]::new));
        long inicio = System.nanoTime();
        importar(c);
        var painel = ctx.relacionamentos().painel().orElseThrow();
        long segundos = (System.nanoTime() - inicio) / 1_000_000_000L;
        assertThat(painel.seguidores()).isEqualTo(120_000);
        assertThat(painel.mutuos()).isEqualTo(60_000);
        assertThat(painel.voceNaoSegue()).isEqualTo(60_000);
        assertThat(segundos).as("importação e painel de 180 mil registros").isLessThan(90);
    }
}
