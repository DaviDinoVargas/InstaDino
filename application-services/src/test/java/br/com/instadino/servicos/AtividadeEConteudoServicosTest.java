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
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtividadeEConteudoServicosTest {

    private static final EstadoDaLicenca PREMIUM = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);
    private static final long DIA = 86_400L;
    private static final long BASE = 1_704_110_400L; // 2024-01-01 12:00 UTC

    @TempDir
    Path pasta;

    private final AtomicReference<EstadoDaLicenca> licenca = new AtomicReference<>(PREMIUM);
    private ContextoLocal ctx;

    @AfterEach
    void fechar() {
        if (ctx != null) {
            ctx.close();
        }
    }

    private static String curtidas(String... contaETempo) {
        StringBuilder j = new StringBuilder("{\"likes_media_likes\":[");
        for (int i = 0; i < contaETempo.length; i += 2) {
            j.append(i > 0 ? "," : "").append("{\"title\":\"").append(contaETempo[i]).append("\",\"string_list_data\":[{\"href\":\"https://www.instagram.com/p/X")
                    .append(i).append("/\",\"timestamp\":").append(contaETempo[i + 1]).append("}]}");
        }
        return j.append("]}").toString();
    }

    private void importar(ConstrutorDeExportacao c) {
        ctx = ContextoLocal.abrir(pasta.resolve("d" + System.nanoTime()), new ProtetorSomenteDesenvolvimento(), licenca::get);
        Path zip = c.escreverEm(pasta.resolve("e-" + System.nanoTime() + ".zip"));
        var r = ctx.importacao().processar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        ctx.importacao().confirmar(r, ctx.sessao().perfilAtivo(), "e.zip", 1L);
    }

    private ConstrutorDeExportacao exportacao() {
        // seguidores: ana, bia | seguindo: ana, caio, dani, eva  =>  não seguem de volta: caio, dani, eva
        var c = new ConstrutorDeExportacao().seguidores("ana", "bia").seguindo("ana", "caio", "dani", "eva");
        c.arquivo("conta/your_instagram_activity/likes/liked_posts.json",
                curtidas("caio", String.valueOf(BASE), "caio", String.valueOf(BASE + DIA), "ana", String.valueOf(BASE + 40 * DIA), "estranho", String.valueOf(BASE + 41 * DIA)));
        c.arquivo("conta/your_instagram_activity/comments/post_comments_1.json", """
                [{"string_map_data":{"Comment":{"value":"legal"},"Media Owner":{"value":"dani"},"Time":{"timestamp":%d}}}]""".formatted(BASE + 2 * DIA));
        c.arquivo("conta/logged_information/recent_searches/profile_searches.json", """
                {"searches_user":[{"string_map_data":{"Search":{"value":"perfil_novo"},"Time":{"timestamp":%d}}},
                                  {"string_map_data":{"Search":{"value":"caio"},"Time":{"timestamp":%d}}},
                                  {"string_map_data":{"Search":{"value":"perfil_novo"},"Time":{"timestamp":%d}}},
                                  {"string_map_data":{"Search":{"value":"duas palavras"},"Time":{"timestamp":%d}}}]}"""
                .formatted(BASE, BASE + 1, BASE + 2, BASE + 3));
        c.arquivo("conta/your_instagram_activity/media/posts_1.json", """
                [{"media":[{"uri":"media/posts/a.jpg","creation_timestamp":%d,"title":"Praia linda"}]},
                 {"media":[{"uri":"media/posts/b.mp4","creation_timestamp":%d,"title":"Montanha"}]},
                 {"media":[{"uri":"media/posts/c.jpg","creation_timestamp":%d,"title":"Praia de novo"}]}]""".formatted(BASE, BASE + 40 * DIA, BASE + 41 * DIA));
        c.arquivo("conta/media/posts/a.jpg", new byte[1000]);
        c.arquivo("conta/media/posts/b.mp4", new byte[5000]);
        return c;
    }

    // ---- plano ----

    @Test
    void freeNaoGuardaAtividadeNemConteudoEOsServicosSaoPremium() {
        licenca.set(EstadoDaLicenca.GRATUITO);
        importar(exportacao());
        assertThatThrownBy(() -> ctx.atividade().resumo()).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> ctx.conteudo().totais()).isInstanceOf(AcessoNegadoException.class);
        // e nada foi gravado: ao virar Premium é preciso importar de novo
        licenca.set(PREMIUM);
        assertThat(ctx.atividade().temDados()).isFalse();
        assertThat(ctx.conteudo().temDados()).isFalse();
        // as listas de relacionamentos continuam funcionando no Free
        assertThat(ctx.relacionamentos().painel()).isPresent();
    }

    // ---- atividade ----

    @Test
    void resumoPorCategoriaComPeriodo() {
        importar(exportacao());
        var resumo = ctx.atividade().resumo();
        assertThat(resumo).extracting(ServicoDeAtividade.ResumoPorCategoria::categoria)
                .containsExactly(CategoriaDeDados.CURTIDAS, CategoriaDeDados.COMENTARIOS, CategoriaDeDados.PESQUISAS);
        var curtidas = resumo.get(0);
        assertThat(curtidas.total()).isEqualTo(4);
        assertThat(curtidas.de()).isEqualTo(Instant.ofEpochSecond(BASE));
        assertThat(curtidas.ate()).isEqualTo(Instant.ofEpochSecond(BASE + 41 * DIA));
    }

    @Test
    void porMesPorDiaEMapaDeCalorUsamOFusoDoComputador() {
        importar(exportacao());
        ZoneId fuso = ZoneId.systemDefault();
        var porMes = ctx.atividade().porMes(CategoriaDeDados.CURTIDAS);
        assertThat(porMes.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(4);
        assertThat(porMes).containsKey(YearMonth.from(Instant.ofEpochSecond(BASE).atZone(fuso)));
        assertThat(ctx.atividade().porDia(CategoriaDeDados.CURTIDAS)).containsKey(Instant.ofEpochSecond(BASE).atZone(fuso).toLocalDate());
        int[][] calor = ctx.atividade().mapaDeCalor(java.util.List.of(CategoriaDeDados.CURTIDAS));
        var z = Instant.ofEpochSecond(BASE).atZone(fuso);
        assertThat(calor[z.getDayOfWeek().getValue() - 1][z.getHour()]).isGreaterThanOrEqualTo(1);
        assertThat(java.util.Arrays.stream(calor).flatMapToInt(java.util.Arrays::stream).sum()).isEqualTo(4);
    }

    @Test
    void rankingContaQuantasVezesENaoInterpretaNada() {
        importar(exportacao());
        var ranking = ctx.atividade().ranking(CategoriaDeDados.CURTIDAS, 10);
        assertThat(ranking.get(0).conta()).isEqualTo("caio");
        assertThat(ranking.get(0).quantidade()).isEqualTo(2);
        assertThat(ranking.get(0).voceSegue()).isTrue();
        assertThat(ranking.get(0).seguidor()).isFalse();
        assertThat(ranking).filteredOn(i -> i.conta().equals("ana")).singleElement().satisfies(i -> {
            assertThat(i.voceSegue()).isTrue();
            assertThat(i.seguidor()).isTrue();
        });
        assertThat(ranking).filteredOn(i -> i.conta().equals("estranho")).singleElement().satisfies(i -> {
            assertThat(i.voceSegue()).isFalse();
            assertThat(i.seguidor()).isFalse();
        });
        assertThat(ServicoDeAtividade.LIMITACAO).contains("não permitem concluir interesse");
    }

    @Test
    void buscaPorTextoCategoriaEPeriodoComPaginacao() {
        importar(exportacao());
        var porTexto = ctx.atividade().buscar(null, "legal", null, null, 0, 10);
        assertThat(porTexto.itens()).singleElement().satisfies(r -> assertThat(r.categoria()).isEqualTo(CategoriaDeDados.COMENTARIOS));
        var periodo = ctx.atividade().buscar(CategoriaDeDados.CURTIDAS, null, Instant.ofEpochSecond(BASE + 30 * DIA), null, 0, 10);
        assertThat(periodo.total()).isEqualTo(2);
        assertThat(ctx.atividade().buscar(CategoriaDeDados.CURTIDAS, null, null, null, 1, 3).itens()).hasSize(1);
    }

    @Test
    void cruzaNaoReciprocosComAtividadeSemAfirmarIntencao() {
        importar(exportacao());
        var c = ctx.atividade().cruzarNaoReciprocos();
        assertThat(c.totalDeNaoReciprocos()).isEqualTo(3);                      // caio, dani, eva
        assertThat(c.comInteracao()).extracting(ServicoDeAtividade.ContaComInteracao::conta).containsExactly("caio", "dani");
        assertThat(c.comInteracao().get(0).interacoes()).isEqualTo(2);        // pesquisas não contam como interação
        assertThat(c.semNenhumaInteracao()).containsExactly("eva");
    }

    @Test
    void seguidoresSemAtividadeSuaEPerfisBuscados() {
        importar(exportacao());
        assertThat(ctx.atividade().seguidoresSemAtividadeSua()).containsExactly("bia");
        var buscados = ctx.atividade().perfisBuscados();
        assertThat(buscados).extracting(ServicoDeAtividade.TermoBuscado::termo).containsExactly("perfil_novo", "caio"); // "duas palavras" não é usuário
        assertThat(buscados.get(0).vezes()).isEqualTo(2);
        assertThat(buscados.get(0).voceSegue()).isFalse();
        assertThat(buscados.get(1).voceSegue()).isTrue();
    }

    @Test
    void atividadeNaoVazaEntrePerfis() {
        importar(exportacao());
        ctx.servicoDePerfis().criar("Outro");
        assertThat(ctx.atividade().resumo()).isEmpty();
        assertThat(ctx.atividade().temDados()).isFalse();
    }

    // ---- conteúdo ----

    @Test
    void conteudoTotaisBuscaEFrequencia() {
        importar(exportacao());
        var totais = ctx.conteudo().totais();
        assertThat(totais.total()).isEqualTo(3);
        assertThat(totais.midias()).extracting(m -> m.tipo(), m -> m.quantidade(), m -> m.bytes())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FOTO", 2, 1000L), org.assertj.core.groups.Tuple.tuple("VIDEO", 1, 5000L));
        var praia = ctx.conteudo().buscar(null, "praia", null, null, null, 0, 10);
        assertThat(praia.total()).isEqualTo(2);
        assertThat(praia.itens().get(0).legenda()).isEqualTo("Praia de novo");       // mais recente primeiro
        assertThat(ctx.conteudo().buscar(null, null, "VIDEO", null, null, 0, 10).itens()).singleElement().satisfies(i -> assertThat(i.legenda()).isEqualTo("Montanha"));
        var f = ctx.conteudo().frequencia(CategoriaDeDados.PUBLICACOES).orElseThrow();
        assertThat(f.publicacoesComData()).isEqualTo(3);
        assertThat(f.mesesComPublicacao()).isGreaterThanOrEqualTo(2);
        assertThat(f.mediaPorMesNoPeriodo()).isGreaterThan(0.0);
        assertThat(ctx.conteudo().frequencia(CategoriaDeDados.REELS)).isEmpty();
        assertThat(ServicoDeConteudo.LIMITACAO).contains("não são copiados");
    }

    // ---- relatórios PDF ----

    private String relatorio(ServicoDeRelatorios.Opcoes o) throws java.io.IOException {
        var saida = new java.io.ByteArrayOutputStream();
        ctx.relatorios().gerarRelatorioDaImportacao(saida, o);
        try (var d = org.apache.pdfbox.Loader.loadPDF(saida.toByteArray())) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(d).replaceAll("\\s+", " ");
        }
    }

    @Test
    void relatorioDeAtividadeEConteudoTrazNumerosMasNaoContasPorPadrao() throws java.io.IOException {
        importar(exportacao());
        String t = relatorio(new ServicoDeRelatorios.Opcoes(false, false, false, false, false, false, false, true, true, false));
        assertThat(t).contains("Atividade registrada na exportação").contains("Curtidas").contains("Estatísticas de conteúdo").contains("Publicações")
                .contains("Fotos").contains("Vídeos").contains("não permitem concluir interesse").contains("não são copiados");
        assertThat(t).doesNotContain("caio").doesNotContain("Contas com mais registros");
        String comRanking = relatorio(new ServicoDeRelatorios.Opcoes(false, false, false, false, false, false, false, true, false, true));
        assertThat(comRanking).contains("Contas com mais registros em curtidas").contains("caio").contains("nomes de contas de terceiros");
    }

    @Test
    void relatorioDeAtividadeEConteudoExigePremium() {
        licenca.set(EstadoDaLicenca.GRATUITO);
        importar(exportacao());
        assertThatThrownBy(() -> relatorio(new ServicoDeRelatorios.Opcoes(false, false, false, false, false, false, false, true, false, false)))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> relatorio(new ServicoDeRelatorios.Opcoes(false, false, false, false, false, false, false, false, true, false)))
                .isInstanceOf(AcessoNegadoException.class);
    }
}
