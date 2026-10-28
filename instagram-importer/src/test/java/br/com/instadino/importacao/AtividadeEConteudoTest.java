package br.com.instadino.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.importacao.Publicacao.TipoDeMidia;
import br.com.instadino.importacao.parser.CorretorDeTexto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtividadeEConteudoTest {

    @TempDir
    Path pasta;

    private ConstrutorDeExportacao base() {
        return new ConstrutorDeExportacao().seguidores("ana").seguindo("ana");
    }

    private ResultadoDaImportacao importar(ConstrutorDeExportacao c) {
        Path zip = c.escreverEm(pasta.resolve("e.zip"));
        return new ImportadorDeExportacao(LimitesDeImportacao.padrao()).importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
    }

    private static List<RegistroDeAtividade> de(ResultadoDaImportacao r, CategoriaDeDados c) {
        return r.atividades().stream().filter(a -> a.categoria() == c).toList();
    }

    // ---- atividade ----

    @Test
    void leCurtidasComTituloComoDonoEHrefDaPublicacao() {
        var c = base();
        c.arquivo("conta/your_instagram_activity/likes/liked_posts.json", """
                {"likes_media_likes":[
                  {"title":"Dono_Um","string_list_data":[{"href":"https://www.instagram.com/p/ABC123/","value":"\\u2764","timestamp":1700000000}]},
                  {"title":"dono.dois","string_list_data":[{"href":"https://www.instagram.com/reel/XYZ/","timestamp":1700003600}]}]}""");
        ResultadoDaImportacao r = importar(c);
        var curtidas = de(r, CategoriaDeDados.CURTIDAS);
        assertThat(curtidas).hasSize(2);
        assertThat(curtidas.get(0).conta()).isEqualTo("dono_um");
        assertThat(curtidas.get(0).referencia()).isEqualTo("https://www.instagram.com/p/ABC123/");
        assertThat(curtidas.get(0).instanteEmSegundos()).isEqualTo(1700000000L);
        assertThat(curtidas.get(0).subtipo()).isEqualTo("liked_posts");
        assertThat(curtidas.get(0).texto()).isNull();
        assertThat(r.categoriasProcessadas()).contains(CategoriaDeDados.CURTIDAS);
    }

    @Test
    void enderecoDePublicacaoNuncaViraNomeDeConta() {
        var c = base();
        c.arquivo("conta/your_instagram_activity/likes/liked_posts.json", """
                [{"string_list_data":[{"href":"https://www.instagram.com/p/ABC123/","timestamp":1700000000}]},
                 {"string_list_data":[{"href":"https://www.instagram.com/reel/XYZ/","timestamp":1700000001}]}]""");
        var curtidas = de(importar(c), CategoriaDeDados.CURTIDAS);
        assertThat(curtidas).hasSize(2).allSatisfy(a -> assertThat(a.conta()).isNull());
    }

    @Test
    void leComentariosPorRotuloEmPortuguesEPorHeuristicaSemRotulo() {
        var c = base();
        c.arquivo("conta/your_instagram_activity/comments/post_comments_1.json", """
                [{"string_map_data":{"Comentário":{"value":"oi"},"Dono da mídia":{"value":"fulano_x"},"Hora":{"timestamp":1700000000}}},
                 {"string_map_data":{"Comment":{"value":"Ã³timo post, parabÃ©ns"},"Media Owner":{"value":"ciclana"},"Time":{"timestamp":1700000100}}},
                 {"string_map_data":{"A":{"value":"curto"},"B":{"value":"um texto bem mais longo aqui"},"C":{"timestamp":1700000200}}}]""");
        var coment = de(importar(c), CategoriaDeDados.COMENTARIOS);
        assertThat(coment).hasSize(3);
        assertThat(coment.get(0).texto()).isEqualTo("oi");           // rótulo "Comentário" vence a heurística de tamanho
        assertThat(coment.get(0).conta()).isEqualTo("fulano_x");
        assertThat(coment.get(1).texto()).isEqualTo("ótimo post, parabéns"); // texto com acentos corrompido é corrigido
        assertThat(coment.get(1).conta()).isEqualTo("ciclana");
        assertThat(coment.get(2).texto()).isEqualTo("um texto bem mais longo aqui");
        assertThat(coment.get(2).instanteEmSegundos()).isEqualTo(1700000200L);
    }

    @Test
    void lePesquisasESalvosEVisualizacoesEInteracoesComStories() {
        var c = base();
        c.arquivo("conta/logged_information/recent_searches/profile_searches.json", """
                {"searches_user":[{"title":"","string_map_data":{"Search":{"value":"alguem_legal"},"Time":{"timestamp":1700000000}}}]}""");
        c.arquivo("conta/your_instagram_activity/saved/saved_posts.json", """
                {"saved_saved_media":[{"title":"salvou_de","string_map_data":{"Saved on":{"href":"https://www.instagram.com/p/Q/","timestamp":1700000300}}}]}""");
        c.arquivo("conta/ads_information/ads_and_topics/posts_viewed.json", """
                {"impressions_history_posts_seen":[{"string_map_data":{"Author":{"value":"autor_a"},"Time":{"timestamp":1700000400}}}]}""");
        c.arquivo("conta/your_instagram_activity/story_interactions/story_likes.json", """
                {"story_activities_story_likes":[{"title":"quem_postou","string_list_data":[{"href":"https://www.instagram.com/stories/quem_postou/1/","timestamp":1700000500}]}]}""");
        ResultadoDaImportacao r = importar(c);
        assertThat(de(r, CategoriaDeDados.PESQUISAS)).singleElement().satisfies(a -> {
            assertThat(a.texto()).isEqualTo("alguem_legal");
            assertThat(a.instanteEmSegundos()).isEqualTo(1700000000L);
        });
        assertThat(de(r, CategoriaDeDados.SALVOS)).singleElement().satisfies(a -> assertThat(a.conta()).isEqualTo("salvou_de"));
        assertThat(de(r, CategoriaDeDados.VISUALIZACOES)).singleElement().satisfies(a -> assertThat(a.conta()).isEqualTo("autor_a"));
        assertThat(de(r, CategoriaDeDados.INTERACOES_STORIES)).singleElement().satisfies(a -> {
            assertThat(a.conta()).isEqualTo("quem_postou");
            assertThat(a.subtipo()).isEqualTo("story_likes");
        });
    }

    @Test
    void leFormatoLabelValuesERegistroUnicoNaRaiz() {
        var c = base();
        c.arquivo("conta/your_instagram_activity/likes/liked_comments.json", """
                [{"timestamp":1700000000,"media":[],"label_values":[
                    {"label":"URL","value":"https://www.instagram.com/p/AAA/"},
                    {"label":"Nome de usuário","value":"dona_do_comentario"}],"fbid":"1"}]""");
        c.arquivo("conta/your_instagram_activity/comments/reels_comments.json", """
                {"timestamp":1700000050,"label_values":[{"label":"Comentário","value":"muito bom"},{"label":"Autor","value":"autor_z"}]}""");
        ResultadoDaImportacao r = importar(c);
        assertThat(de(r, CategoriaDeDados.CURTIDAS)).singleElement().satisfies(a -> {
            assertThat(a.conta()).isEqualTo("dona_do_comentario");
            assertThat(a.instanteEmSegundos()).isEqualTo(1700000000L);
        });
        assertThat(de(r, CategoriaDeDados.COMENTARIOS)).singleElement().satisfies(a -> {
            assertThat(a.texto()).isEqualTo("muito bom");
            assertThat(a.conta()).isEqualTo("autor_z");
        });
    }

    @Test
    void carimboEmMilissegundosEConvertidoParaSegundos() {
        var c = base();
        c.arquivo("conta/your_instagram_activity/likes/liked_posts.json", "[{\"title\":\"x1\",\"string_list_data\":[{\"timestamp\":1700000000123}]}]");
        assertThat(de(importar(c), CategoriaDeDados.CURTIDAS).get(0).instanteEmSegundos()).isEqualTo(1700000000L);
    }

    @Test
    void elementosSemNadaUtilSaoDescartadosComContagem() {
        var c = base();
        c.arquivo("conta/your_instagram_activity/likes/liked_posts.json", "[{\"foo\":1},{\"title\":\"ok_user\",\"string_list_data\":[{\"timestamp\":5}]},42]");
        ResultadoDaImportacao r = importar(c);
        assertThat(de(r, CategoriaDeDados.CURTIDAS)).hasSize(1);
        var d = r.diagnosticos().stream().filter(x -> x.categoria() == CategoriaDeDados.CURTIDAS).findFirst().orElseThrow();
        assertThat(d.elementosLidos()).isEqualTo(3);
        assertThat(d.validos()).isEqualTo(1);
        assertThat(d.descartados()).isEqualTo(2);
    }

    @Test
    void atividadeEmHtmlEAvisadaENaoLida() {
        var c = base();
        c.arquivo("conta/your_instagram_activity/likes/liked_posts.html", "<html><body>x</body></html>");
        ResultadoDaImportacao r = importar(c);
        assertThat(de(r, CategoriaDeDados.CURTIDAS)).isEmpty();
        assertThat(r.avisos()).anyMatch(a -> a.contains("HTML"));
        assertThat(r.categoriasIncompletas()).contains(CategoriaDeDados.CURTIDAS);
    }

    @Test
    void configuracaoSemAtividadeNemConteudoNaoLeNada() {
        var c = base();
        c.arquivo("conta/your_instagram_activity/likes/liked_posts.json", "[{\"title\":\"ok_user\",\"string_list_data\":[{\"timestamp\":5}]}]");
        c.arquivo("conta/your_instagram_activity/media/posts_1.json", "[{\"media\":[{\"uri\":\"media/posts/a.jpg\",\"creation_timestamp\":5,\"title\":\"x\"}]}]");
        Path zip = c.escreverEm(pasta.resolve("e.zip"));
        var r = new ImportadorDeExportacao(LimitesDeImportacao.padrao()).importar(zip, ConfiguracaoDeImportacao.padrao().semAtividadeNemConteudo(),
                new Cancelamento(), OuvinteDeProgresso.NENHUM);
        assertThat(r.atividades()).isEmpty();
        assertThat(r.publicacoes()).isEmpty();
        assertThat(r.relacionamentos()).isNotEmpty();
    }

    // ---- conteúdo ----

    @Test
    void lePublicacoesStoriesEReelsEResolveMidiasPeloInventario() {
        var c = base();
        c.arquivo("conta/your_instagram_activity/media/posts_1.json", """
                [{"media":[{"uri":"media/posts/202401/foto1.jpg","creation_timestamp":1704067200,"title":"Legenda com acentuaÃ§Ã£o"},
                           {"uri":"media/posts/202401/video1.mp4","creation_timestamp":1704067200}]},
                 {"title":"sem midia no zip","media":[{"uri":"media/posts/202402/sumiu.png","creation_timestamp":1706745600}]}]""");
        c.arquivo("conta/your_instagram_activity/media/stories.json", """
                {"ig_stories":[{"uri":"media/stories/202403/s1.mp4","creation_timestamp":1709251200,"title":""}]}""");
        c.arquivo("conta/your_instagram_activity/media/reels.json", """
                {"ig_reels_media":[{"media":[{"uri":"media/reels/r1.mp4","creation_timestamp":1711929600,"title":"meu reel"}]}]}""");
        c.arquivo("conta/media/posts/202401/foto1.jpg", new byte[2048]);
        c.arquivo("conta/media/posts/202401/video1.mp4", new byte[4096]);
        c.arquivo("conta/media/stories/202403/s1.mp4", new byte[1000]);
        ResultadoDaImportacao r = importar(c);
        var posts = r.publicacoes().stream().filter(p -> p.categoria() == CategoriaDeDados.PUBLICACOES).toList();
        assertThat(posts).hasSize(2);
        assertThat(posts.get(0).legenda()).isEqualTo("Legenda com acentuação");
        assertThat(posts.get(0).instanteEmSegundos()).isEqualTo(1704067200L);
        assertThat(posts.get(0).midias()).extracting(m -> m.tipo()).containsExactly(TipoDeMidia.FOTO, TipoDeMidia.VIDEO);
        assertThat(posts.get(0).midias().get(0).tamanhoEmBytes()).isEqualTo(2048L);
        assertThat(posts.get(0).midias().get(0).caminho()).doesNotContain("conta/");
        assertThat(posts.get(1).midias()).singleElement().satisfies(m -> assertThat(m.tamanhoEmBytes()).isNull()); // mídia não encontrada no ZIP
        var stories = r.publicacoes().stream().filter(p -> p.categoria() == CategoriaDeDados.STORIES).toList();
        assertThat(stories).singleElement().satisfies(s -> {
            assertThat(s.legenda()).isNull();
            assertThat(s.midias()).hasSize(1);
        });
        assertThat(r.publicacoes().stream().filter(p -> p.categoria() == CategoriaDeDados.REELS)).singleElement()
                .satisfies(p -> assertThat(p.legenda()).isEqualTo("meu reel"));
    }

    @Test
    void midiaComCaminhoHostilNaoEscapaNemEExtraida() {
        var c = base();
        c.arquivo("conta/your_instagram_activity/media/posts_1.json",
                "[{\"media\":[{\"uri\":\"../../../etc/passwd\",\"creation_timestamp\":5,\"title\":\"x\"},{\"uri\":\"C:\\\\Windows\\\\win.ini\",\"creation_timestamp\":5}]}]");
        var p = importar(c).publicacoes().get(0);
        // o caminho é só texto de catálogo: nada é aberto, resolvido em disco ou extraído
        assertThat(p.midias()).hasSize(2).allSatisfy(m -> assertThat(m.tamanhoEmBytes()).isNull());
    }

    // ---- corretor de texto ----

    @Test
    void corretorDeTextoSoMexeEmTextoCorrompido() {
        assertThat(CorretorDeTexto.corrigir("Ã§Ã£o")).isEqualTo("ção");
        assertThat(CorretorDeTexto.corrigir("Olá, ação")).isEqualTo("Olá, ação");           // já correto (á isolado não é UTF-8 válido)
        assertThat(CorretorDeTexto.corrigir("日本語 and Ã©")).isEqualTo("日本語 and Ã©");       // tem caracteres fora do Latin-1
        assertThat(CorretorDeTexto.corrigir("plain ascii")).isEqualTo("plain ascii");
        assertThat(CorretorDeTexto.corrigir(null)).isNull();
        assertThat(CorretorDeTexto.corrigir("")).isEmpty();
        String emoji = new String("😀".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        assertThat(CorretorDeTexto.corrigir(emoji)).isEqualTo("😀");
    }

    @Test
    void volumeGrandeDeAtividadeELidoSemEstourar() {
        StringBuilder j = new StringBuilder("[");
        for (int i = 0; i < 50_000; i++) {
            j.append(i > 0 ? "," : "").append("{\"title\":\"u").append(i % 500).append("\",\"string_list_data\":[{\"timestamp\":").append(1_700_000_000L + i).append("}]}");
        }
        j.append("]");
        var c = base();
        c.arquivo("conta/your_instagram_activity/likes/liked_posts.json", j.toString());
        assertThat(de(importar(c), CategoriaDeDados.CURTIDAS)).hasSize(50_000);
    }
}
