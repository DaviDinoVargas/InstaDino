package br.com.instadino.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.nucleo.dominio.Relacionamento;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportadorDeExportacaoTest {

    @TempDir
    Path pasta;

    private final ImportadorDeExportacao importador = new ImportadorDeExportacao(LimitesDeImportacao.padrao());

    private ResultadoDaImportacao importar(ConstrutorDeExportacao c) {
        Path zip = c.escreverEm(pasta.resolve("exportacao.zip"));
        return importador.importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
    }

    private static List<String> nomes(ResultadoDaImportacao r, TipoDeRelacionamento tipo) {
        return r.lista(tipo).stream().map(x -> x.usuario().valor()).toList();
    }

    @Test
    void importaSeguidoresESeguindoDeUmaExportacaoValida() {
        ResultadoDaImportacao r = importar(ConstrutorDeExportacao.exemplo());
        assertThat(nomes(r, TipoDeRelacionamento.SEGUIDOR)).containsExactly("ana", "bruno", "carla", "diego");
        assertThat(nomes(r, TipoDeRelacionamento.SEGUINDO)).containsExactly("bruno", "carla", "eduardo", "fernanda");
        assertThat(r.registrosValidos()).isEqualTo(8);
        assertThat(r.registrosInvalidos()).isZero();
        assertThat(r.permiteComparacaoDeSeguidores()).isTrue();
        assertThat(r.versaoDoParser()).isEqualTo(ImportadorDeExportacao.VERSAO_DO_PARSER);
        assertThat(r.categoriasProcessadas()).contains(CategoriaDeDados.SEGUIDORES, CategoriaDeDados.SEGUINDO);
    }

    @Test
    void preservaOCarimboDeTempoDaExportacao() {
        ResultadoDaImportacao r = importar(ConstrutorDeExportacao.exemplo());
        assertThat(r.lista(TipoDeRelacionamento.SEGUIDOR)).allSatisfy(x -> assertThat(x.instanteEmSegundos()).isNotNull().isPositive());
    }

    @Test
    void leFormatoHtmlEIgnoraLinksQueNaoSaoDePerfil() {
        var c = new ConstrutorDeExportacao()
                .seguidoresHtml("ana", "bruno")
                .seguindo("ana");
        c.arquivo("connections/followers_and_following/followers_2.html",
                "<html><body><script>alert(1)</script><a href=\"https://evil.example.com/ana2\">ana2</a>"
                        + "<a href=\"javascript:alert(1)\">x</a><a href=\"https://www.instagram.com/carla\">carla</a></body></html>");
        ResultadoDaImportacao r = importar(c);
        assertThat(nomes(r, TipoDeRelacionamento.SEGUIDOR)).containsExactly("ana", "bruno", "carla");
    }

    @Test
    void juntaVariasPartesDeSeguidoresEConsideraDuplicatasEntreArquivos() {
        var c = new ConstrutorDeExportacao()
                .seguidoresParte(1, "ana", "bruno")
                .seguidoresParte(2, "bruno", "carla")
                .seguindo("ana");
        ResultadoDaImportacao r = importar(c);
        assertThat(nomes(r, TipoDeRelacionamento.SEGUIDOR)).containsExactly("ana", "bruno", "carla");
        assertThat(r.registrosDuplicados()).isEqualTo(1);
    }

    @Test
    void normalizaCaixaEDescartaDuplicatasEInvalidosContandoOsDescartes() {
        var c = new ConstrutorDeExportacao().seguindo("ana");
        c.arquivo("connections/followers_and_following/followers_1.json",
                "[{\"string_list_data\":[{\"value\":\"Maria.Silva\",\"timestamp\":200}]},"
                        + "{\"string_list_data\":[{\"value\":\"maria.silva\",\"timestamp\":100}]},"
                        + "{\"string_list_data\":[{\"value\":\"nome com espaço\",\"href\":\"https://evil.com/x\"}]},"
                        + "{\"string_list_data\":[]},"
                        + "42,"
                        + "{\"string_list_data\":[{\"value\":\"@joao\"}]}]");
        ResultadoDaImportacao r = importar(c);
        assertThat(r.lista(TipoDeRelacionamento.SEGUIDOR)).extracting(x -> x.usuario().valor()).containsExactly("joao", "maria.silva");
        Relacionamento maria = r.lista(TipoDeRelacionamento.SEGUIDOR).get(1);
        assertThat(maria.instanteEmSegundos()).as("mantém o menor carimbo de tempo").isEqualTo(100L);
        assertThat(r.registrosDuplicados()).isEqualTo(1);
        assertThat(r.registrosInvalidos()).isEqualTo(3);
        assertThat(r.avisos()).anyMatch(a -> a.contains("descartados"));
    }

    @Test
    void usaOTituloQuandoNaoHaValorEAUrlComoUltimoRecurso() {
        var c = new ConstrutorDeExportacao().seguidores("ana");
        c.arquivo("connections/followers_and_following/following.json",
                "{\"relationships_following\":[{\"title\":\"Bruno\",\"string_list_data\":[{\"href\":\"https://www.instagram.com/_u/bruno\"}]},"
                        + "{\"title\":\"\",\"string_list_data\":[{\"href\":\"https://www.instagram.com/_u/carla\"}]}]}");
        ResultadoDaImportacao r = importar(c);
        assertThat(nomes(r, TipoDeRelacionamento.SEGUINDO)).containsExactly("bruno", "carla");
    }

    @Test
    void importaOutrasListasDeRelacionamento() {
        var c = ConstrutorDeExportacao.exemplo().pendentes("p1").bloqueados("b1", "b2").restritos("r1").amigosProximos("f1");
        ResultadoDaImportacao r = importar(c);
        assertThat(nomes(r, TipoDeRelacionamento.SOLICITACAO_PENDENTE)).containsExactly("p1");
        assertThat(nomes(r, TipoDeRelacionamento.BLOQUEADO)).containsExactly("b1", "b2");
        assertThat(nomes(r, TipoDeRelacionamento.RESTRITO)).containsExactly("r1");
        assertThat(nomes(r, TipoDeRelacionamento.FAVORITO)).containsExactly("f1");
    }

    @Test
    void inventariaTodasAsCategoriasENaoProcessaAsSensiveisSemAutorizacao() {
        var c = ConstrutorDeExportacao.exemplo().comCategoriasDiversas();
        Path zip = c.escreverEm(pasta.resolve("x.zip"));
        InventarioDoZip inv = importador.inspecionar(zip, new Cancelamento(), OuvinteDeProgresso.NENHUM);
        assertThat(inv.arquivosPorCategoria()).containsKeys(
                CategoriaDeDados.SEGUIDORES, CategoriaDeDados.SEGUINDO, CategoriaDeDados.PUBLICACOES, CategoriaDeDados.MIDIA,
                CategoriaDeDados.CURTIDAS, CategoriaDeDados.COMENTARIOS, CategoriaDeDados.SALVOS, CategoriaDeDados.PESQUISAS,
                CategoriaDeDados.MENSAGENS, CategoriaDeDados.SEGURANCA, CategoriaDeDados.PERFIL,
                CategoriaDeDados.ANUNCIOS_E_PREFERENCIAS, CategoriaDeDados.DESCONHECIDA);
        assertThat(inv.categoriasSensiveisPresentes()).containsExactlyInAnyOrder(
                CategoriaDeDados.MENSAGENS, CategoriaDeDados.SEGURANCA, CategoriaDeDados.PERFIL);

        ResultadoDaImportacao r = importador.importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        assertThat(r.categoriasProcessadas()).doesNotContain(CategoriaDeDados.MENSAGENS, CategoriaDeDados.SEGURANCA);
        assertThat(r.avisos()).anyMatch(a -> a.contains("Mensagens") && a.contains("não foi autorizada"));
        assertThat(r.avisos()).anyMatch(a -> a.contains("não reconhecido"));
    }

    @Test
    void avisaQuandoAsListasDeSeguidoresNaoExistem() {
        var c = new ConstrutorDeExportacao().comCategoriasDiversas();
        ResultadoDaImportacao r = importar(c);
        assertThat(r.permiteComparacaoDeSeguidores()).isFalse();
        assertThat(r.avisos()).anyMatch(a -> a.contains("seguidores não foi encontrada"));
        assertThat(r.avisos()).anyMatch(a -> a.contains("contas seguidas não foi encontrada"));
    }

    @Test
    void arquivoJsonTruncadoMarcaACategoriaComoIncompletaSemDerrubarAImportacao() {
        var c = new ConstrutorDeExportacao().seguindo("ana", "bruno");
        c.arquivo("connections/followers_and_following/followers_1.json", "[{\"string_list_data\":[{\"value\":\"ana\"}]},{\"string_list_dat");
        ResultadoDaImportacao r = importar(c);
        assertThat(r.categoriasIncompletas()).contains(CategoriaDeDados.SEGUIDORES);
        assertThat(r.permiteComparacaoDeSeguidores()).isFalse();
        assertThat(nomes(r, TipoDeRelacionamento.SEGUINDO)).containsExactly("ana", "bruno");
        assertThat(r.avisos()).anyMatch(a -> a.contains("incompleta") || a.contains("ilegível"));
    }

    @Test
    void jsonInvalidoEEstruturasInesperadasSaoTratadosSemExcecao() {
        for (String conteudo : List.of("não é json", "42", "\"texto\"", "{}", "{\"a\":1}", "[", "", "null")) {
            var c = new ConstrutorDeExportacao().seguindo("ana");
            c.arquivo("connections/followers_and_following/followers_1.json", conteudo);
            ResultadoDaImportacao r = importar(c);
            assertThat(r.categoriasIncompletas()).as("conteúdo: %s", conteudo).contains(CategoriaDeDados.SEGUIDORES);
        }
    }

    @Test
    void jsonComAninhamentoProfundoNaoEstouraAPilha() {
        var c = new ConstrutorDeExportacao().seguindo("ana");
        String profundo = "[{\"string_list_data\":" + "[".repeat(20000) + "]".repeat(20000) + "}]";
        c.arquivo("connections/followers_and_following/followers_1.json", profundo);
        ResultadoDaImportacao r = importar(c);
        assertThat(r.categoriasIncompletas()).contains(CategoriaDeDados.SEGUIDORES);
    }

    @Test
    void textoGiganteEmUmCampoEContido() {
        var c = new ConstrutorDeExportacao().seguindo("ana");
        c.arquivo("connections/followers_and_following/followers_1.json",
                "[{\"title\":\"" + "a".repeat(500_000) + "\",\"string_list_data\":[]}]");
        ResultadoDaImportacao r = importar(c);
        assertThat(r.categoriasIncompletas()).contains(CategoriaDeDados.SEGUIDORES);
    }

    @Test
    void relatoriaProgressoReal() {
        Path zip = ConstrutorDeExportacao.exemplo().escreverEm(pasta.resolve("p.zip"));
        List<String> etapas = new java.util.ArrayList<>();
        importador.importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), (etapa, atual, total) -> etapas.add(etapa));
        assertThat(etapas).isNotEmpty().anyMatch(e -> e.startsWith("Lendo"));
    }

    @Test
    void nomesDeUsuarioSaoObjetosValidados() {
        ResultadoDaImportacao r = importar(ConstrutorDeExportacao.exemplo());
        assertThat(r.lista(TipoDeRelacionamento.SEGUIDOR)).allSatisfy(x -> assertThat(NomeDeUsuario.tentar(x.usuario().valor())).isPresent());
        assertThat(Set.of(r.relacionamentos().keySet().toArray())).isNotEmpty();
    }
}
