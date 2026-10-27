package br.com.instadino.persistencia.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.Chaves;
import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeAtividade.Filtro;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.AtividadeGravada;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.MidiaGravada;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.NovaImportacao;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.PublicacaoGravada;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoriosDeAtividadeEConteudoTest {

    @TempDir
    Path pasta;

    private BancoLocal banco;
    private RepositorioDeImportacoes importacoes;
    private RepositorioDeAtividade atividade;
    private RepositorioDeConteudo conteudo;
    private long perfil;

    @BeforeEach
    void preparar() {
        banco = BancoLocal.abrir(pasta.resolve("a.db"), Chaves.A, CarregadorDeMigracoes.doClasspath());
        perfil = new RepositorioDePerfis(banco).criar("P", null).id();
        importacoes = new RepositorioDeImportacoes(banco);
        atividade = new RepositorioDeAtividade(banco);
        conteudo = new RepositorioDeConteudo(banco);
    }

    @AfterEach
    void fechar() {
        banco.close();
    }

    private static AtividadeGravada a(String cat, String conta, String texto, Long t) {
        return new AtividadeGravada(cat, "arq", conta, texto, null, t);
    }

    private String gravar(List<AtividadeGravada> ativ, List<PublicacaoGravada> pubs) {
        return importacoes.salvar(new NovaImportacao(perfil, "1.4.0", "x", "e.zip", 1L, 0, 0, Map.of(), List.of(), null, ativ, pubs), 0);
    }

    @Test
    void contagemRankingPeriodoEInstantes() {
        String id = gravar(List.of(a("CURTIDAS", "ana", null, 100L), a("CURTIDAS", "ana", null, 300L), a("CURTIDAS", "bia", null, 200L),
                a("CURTIDAS", null, null, null), a("COMENTARIOS", "ana", "oi", 400L)), List.of());
        assertThat(atividade.contagemPorCategoria(id)).containsEntry("CURTIDAS", 4).containsEntry("COMENTARIOS", 1);
        assertThat(atividade.periodo(id, "CURTIDAS")).containsExactly(100L, 300L);
        assertThat(atividade.periodo(id, "SALVOS")).isEmpty();
        assertThat(atividade.instantes(id, "CURTIDAS")).containsExactly(100L, 200L, 300L);
        assertThat(atividade.ranking(id, "CURTIDAS", 10)).extracting(RepositorioDeAtividade.ContaContada::conta, RepositorioDeAtividade.ContaContada::quantidade)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ana", 2), org.assertj.core.groups.Tuple.tuple("bia", 1));
        assertThat(atividade.totaisPorConta(id, List.of("CURTIDAS", "COMENTARIOS"))).containsEntry("ana", 3).containsEntry("bia", 1);
    }

    @Test
    void buscaComFiltrosPaginacaoEOrdemMaisRecentePrimeiro() {
        List<AtividadeGravada> lista = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            lista.add(a("CURTIDAS", "conta" + i, null, 1000L + i));
        }
        lista.add(a("COMENTARIOS", "x", "Comentário legal", 5000L));
        String id = gravar(lista, List.of());
        var pagina0 = atividade.buscar(id, new Filtro("CURTIDAS", null, null, null), 0, 10);
        assertThat(pagina0.total()).isEqualTo(25);
        assertThat(pagina0.itens()).hasSize(10);
        assertThat(pagina0.itens().get(0).conta()).isEqualTo("conta24");
        assertThat(atividade.buscar(id, new Filtro("CURTIDAS", null, null, null), 2, 10).itens()).hasSize(5);
        assertThat(atividade.buscar(id, new Filtro(null, "LEGAL", null, null), 0, 10).itens()).extracting(RepositorioDeAtividade.Linha::texto)
                .containsExactly("Comentário legal");
        assertThat(atividade.buscar(id, new Filtro("CURTIDAS", null, 1010L, 1012L), 0, 10).itens()).hasSize(3);
        assertThat(atividade.buscar(id, new Filtro(null, "conta1", null, null), 0, 50).total()).isEqualTo(11); // conta1, conta10..conta19
    }

    @Test
    void curingasDoLikeSaoEscapadosEInjecaoDeSqlEApenasTexto() {
        String id = gravar(List.of(a("COMENTARIOS", "u1", "100% bom", 1L), a("COMENTARIOS", "u2", "100 bom", 2L), a("COMENTARIOS", "u3", "a_b", 3L),
                a("COMENTARIOS", "u4", "axb", 4L)), List.of());
        assertThat(atividade.buscar(id, new Filtro(null, "100%", null, null), 0, 10).itens()).extracting(RepositorioDeAtividade.Linha::conta).containsExactly("u1");
        assertThat(atividade.buscar(id, new Filtro(null, "a_b", null, null), 0, 10).itens()).extracting(RepositorioDeAtividade.Linha::conta).containsExactly("u3");
        assertThat(atividade.buscar(id, new Filtro(null, "'; DROP TABLE atividade; --", null, null), 0, 10).total()).isZero();
        assertThat(atividade.contagemPorCategoria(id)).containsEntry("COMENTARIOS", 4);
    }

    @Test
    void importacoesSaoIsoladasEExclusaoApagaEmCascata() {
        String um = gravar(List.of(a("CURTIDAS", "ana", null, 1L)), List.of(new PublicacaoGravada("PUBLICACOES", "l1", 1L, List.of(new MidiaGravada("m/a.jpg", "FOTO", 10L)))));
        String dois = gravar(List.of(a("CURTIDAS", "bia", null, 2L), a("CURTIDAS", "bia", null, 3L)), List.of());
        assertThat(atividade.contagemPorCategoria(um)).containsEntry("CURTIDAS", 1);
        assertThat(atividade.contagemPorCategoria(dois)).containsEntry("CURTIDAS", 2);
        assertThat(conteudo.contagemPorCategoria(dois)).isEmpty();
        importacoes.excluir(um);
        assertThat(atividade.contagemPorCategoria(um)).isEmpty();
        assertThat(conteudo.contagemPorCategoria(um)).isEmpty();
        assertThat(conteudo.totaisDeMidia(um)).isEmpty();
        assertThat(atividade.contagemPorCategoria(dois)).containsEntry("CURTIDAS", 2);
    }

    @Test
    void conteudoComMidiasFiltrosEEstatisticas() {
        var p1 = new PublicacaoGravada("PUBLICACOES", "Praia 100%", 3000L, List.of(new MidiaGravada("m/1.jpg", "FOTO", 100L), new MidiaGravada("m/2.mp4", "VIDEO", 900L)));
        var p2 = new PublicacaoGravada("PUBLICACOES", "Montanha", 2000L, List.of(new MidiaGravada("m/3.jpg", "FOTO", null)));
        var p3 = new PublicacaoGravada("STORIES", null, 1000L, List.of(new MidiaGravada("m/4.mp4", "VIDEO", 50L)));
        String id = gravar(List.of(), List.of(p1, p2, p3));
        assertThat(conteudo.contagemPorCategoria(id)).containsEntry("PUBLICACOES", 2).containsEntry("STORIES", 1);
        var todos = conteudo.buscar(id, new RepositorioDeConteudo.Filtro(null, null, null, null, null), 0, 10);
        assertThat(todos.total()).isEqualTo(3);
        assertThat(todos.itens()).extracting(RepositorioDeConteudo.Item::legenda).containsExactly("Praia 100%", "Montanha", null);
        assertThat(todos.itens().get(0).midias()).extracting(RepositorioDeConteudo.Midia::caminho).containsExactly("m/1.jpg", "m/2.mp4");
        assertThat(todos.itens().get(1).midias().get(0).tamanho()).isNull();
        assertThat(conteudo.buscar(id, new RepositorioDeConteudo.Filtro("PUBLICACOES", null, "VIDEO", null, null), 0, 10).itens())
                .extracting(RepositorioDeConteudo.Item::legenda).containsExactly("Praia 100%");
        assertThat(conteudo.buscar(id, new RepositorioDeConteudo.Filtro(null, "100%", null, null, null), 0, 10).total()).isEqualTo(1);
        assertThat(conteudo.buscar(id, new RepositorioDeConteudo.Filtro(null, null, null, 1500L, 2500L), 0, 10).itens())
                .extracting(RepositorioDeConteudo.Item::legenda).containsExactly("Montanha");
        assertThat(conteudo.totaisDeMidia(id)).extracting(RepositorioDeConteudo.TotalDeMidia::tipo, RepositorioDeConteudo.TotalDeMidia::quantidade,
                RepositorioDeConteudo.TotalDeMidia::bytes).containsExactly(org.assertj.core.groups.Tuple.tuple("FOTO", 2, 100L),
                org.assertj.core.groups.Tuple.tuple("VIDEO", 2, 950L));
        assertThat(conteudo.instantes(id, "PUBLICACOES")).containsExactly(2000L, 3000L);
    }

    @Test
    void paginaDeConteudoTrazAsMidiasSoDaPagina() {
        List<PublicacaoGravada> pubs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            pubs.add(new PublicacaoGravada("PUBLICACOES", "p" + i, 100L + i, List.of(new MidiaGravada("m/" + i + ".jpg", "FOTO", 1L))));
        }
        String id = gravar(List.of(), pubs);
        var pag = conteudo.buscar(id, new RepositorioDeConteudo.Filtro(null, null, null, null, null), 1, 10);
        assertThat(pag.itens()).hasSize(10);
        assertThat(pag.itens()).allSatisfy(i -> assertThat(i.midias()).hasSize(1));
        assertThat(pag.itens().get(0).legenda()).isEqualTo("p19");
    }

    @Test
    void gravacaoGrandeNaMesmaTransacao() {
        List<AtividadeGravada> lista = new ArrayList<>();
        for (int i = 0; i < 120_000; i++) {
            lista.add(a("CURTIDAS", "c" + (i % 1000), null, (long) i));
        }
        long inicio = System.nanoTime();
        String id = gravar(lista, List.of());
        assertThat(atividade.contagemPorCategoria(id)).containsEntry("CURTIDAS", 120_000);
        assertThat((System.nanoTime() - inicio) / 1_000_000_000L).isLessThan(60L);
    }

    /** Regressão: wasNull() valia para a coluna errada e o tamanho do arquivo era perdido quando o nome era nulo. */
    @Test
    void tamanhoDaImportacaoNaoDependeDoNomeDoArquivo() {
        String semNome = importacoes.salvar(new NovaImportacao(perfil, "1", "x", null, 1234L, 0, 0, Map.of(), List.of(), null), 0);
        String semTamanho = importacoes.salvar(new NovaImportacao(perfil, "1", "x", "a.zip", null, 0, 0, Map.of(), List.of(), null), 0);
        assertThat(importacoes.obter(semNome).orElseThrow().tamanhoEmBytes()).isEqualTo(1234L);
        assertThat(importacoes.obter(semTamanho).orElseThrow().tamanhoEmBytes()).isNull();
        assertThat(importacoes.listar(perfil)).extracting(r -> r.tamanhoEmBytes()).containsExactlyInAnyOrder(1234L, null);
    }
}
