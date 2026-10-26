package br.com.instadino.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Formatos alternativos de listas e o diagnóstico por arquivo (estrutura e contagens, nunca nomes de usuário). */
class DiagnosticoEFormatosTest {

    @TempDir
    Path pasta;

    private ResultadoDaImportacao importar(ConstrutorDeExportacao c) {
        Path zip = c.escreverEm(pasta.resolve("e.zip"));
        return new ImportadorDeExportacao(LimitesDeImportacao.padrao()).importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM);
    }

    private static List<String> nomes(ResultadoDaImportacao r, TipoDeRelacionamento t) {
        return r.lista(t).stream().map(x -> x.usuario().valor()).toList();
    }

    // ---- label_values (formato mais recente) ----

    @Test
    void leSolicitacoesPendentesNoFormatoLabelValues() {
        var c = new ConstrutorDeExportacao().seguidores("ana").seguindo("ana");
        c.arquivo("connections/followers_and_following/pending_follow_requests.json", """
                {"relationships_follow_requests_sent": [
                  {"timestamp": 1704067200, "media": [], "label_values": [
                     {"label": "URL", "value": "https://www.instagram.com/carla_x", "href": "https://www.instagram.com/carla_x"},
                     {"label": "Nome", "value": "Carla Silva"}]},
                  {"timestamp": 1704153600, "media": [], "label_values": [
                     {"label": "Nome de usuário", "value": "diego.m"}]},
                  {"timestamp": 1704153600, "media": [], "label_values": [
                     {"label": "Nome", "value": "Sem usuário Algum"}]}
                ]}""");
        ResultadoDaImportacao r = importar(c);
        assertThat(nomes(r, TipoDeRelacionamento.SOLICITACAO_PENDENTE)).containsExactly("carla_x", "diego.m");
        assertThat(r.lista(TipoDeRelacionamento.SOLICITACAO_PENDENTE).get(0).instanteEmSegundos()).isEqualTo(1704067200L);
        assertThat(r.registrosInvalidos()).isEqualTo(1);
    }

    @Test
    void leSeguidoresSemValueEComHrefDeUsuarioCom_u() {
        var c = new ConstrutorDeExportacao().seguindo("ana");
        c.arquivo("connections/followers_and_following/followers_1.json", """
                [{"title": "", "media_list_data": [], "string_list_data": [{"href": "https://www.instagram.com/_u/bruno_1", "timestamp": 1700000000}]},
                 {"title": "carla", "string_list_data": []},
                 {"string_list_data": [{"href": "https://www.instagram.com/diego", "value": "diego", "timestamp": 1700000001}]}]""");
        ResultadoDaImportacao r = importar(c);
        assertThat(nomes(r, TipoDeRelacionamento.SEGUIDOR)).containsExactly("bruno_1", "carla", "diego");
    }

    // ---- diagnóstico ----

    @Test
    void diagnosticoMostraContagensPorArquivoEOMotivoDosDescartes() {
        var c = new ConstrutorDeExportacao();
        c.arquivo("conta-de-maria-2026/connections/followers_and_following/followers_1.json", """
                [{"title":"","string_list_data":[{"href":"https://www.instagram.com/ana","value":"ana","timestamp":1}]},
                 {"title":"","string_list_data":[{"href":"https://www.instagram.com/ana","value":"ana","timestamp":2}]},
                 {"title":"Nome Com Espaço","string_list_data":[{"href":"https://example.com/x","value":"Nome Com Espaço"}]}]""");
        c.arquivo("conta-de-maria-2026/connections/followers_and_following/following.json",
                "{\"relationships_following\":[{\"title\":\"bruno\",\"string_list_data\":[{\"href\":\"https://www.instagram.com/_u/bruno\",\"timestamp\":5}]}]}");
        ResultadoDaImportacao r = importar(c);
        assertThat(r.diagnosticos()).hasSize(2);
        var seguidores = r.diagnosticos().stream().filter(d -> d.categoria() == CategoriaDeDados.SEGUIDORES).findFirst().orElseThrow();
        assertThat(seguidores.elementosLidos()).isEqualTo(3);
        assertThat(seguidores.validos()).isEqualTo(1);
        assertThat(seguidores.duplicados()).isEqualTo(1);
        assertThat(seguidores.descartados()).isEqualTo(1);
        assertThat(seguidores.invalidosPorMotivo()).containsKey("sem nome de usuário válido");
        assertThat(seguidores.erro()).isNull();
    }

    @Test
    void diagnosticoNuncaTrazNomesDeUsuarioNemOValorDosCampos() {
        var c = new ConstrutorDeExportacao();
        c.arquivo("conta-de-maria-2026/connections/followers_and_following/followers_1.json", """
                [{"title":"","string_list_data":[{"href":"https://www.instagram.com/segredo_usuario","value":"segredo_usuario","timestamp":1}]}]""");
        ResultadoDaImportacao r = importar(c);
        String texto = r.diagnosticos().stream().map(DiagnosticoDeArquivo::comoTexto).reduce("", String::concat);
        assertThat(texto).doesNotContain("segredo_usuario").doesNotContain("maria").doesNotContain("instagram.com");
        // mas mostra a FORMA, que é o que permite entender um formato novo
        assertThat(texto).contains("{string_list_data:[1]{href:texto,timestamp:num,value:texto},title:vazio}").contains("followers_1.json");
    }

    @Test
    void caminhoDoDiagnosticoNaoTemAPastaRaizDaExportacao() {
        assertThat(DiagnosticoDeArquivo.caminhoSemRaiz("instagram-maria-2026-01-01-abc/connections/followers_and_following/followers_1.json"))
                .isEqualTo("connections/followers_and_following/followers_1.json");
        assertThat(DiagnosticoDeArquivo.caminhoSemRaiz("followers_and_following/following.json")).isEqualTo("followers_and_following/following.json");
        assertThat(DiagnosticoDeArquivo.caminhoSemRaiz("pasta-do-usuario/qualquer/coisa/arquivo.json")).isEqualTo("coisa/arquivo.json");
        assertThat(DiagnosticoDeArquivo.caminhoSemRaiz("arquivo.json")).isEqualTo("arquivo.json");
    }

    @Test
    void arquivoComEstruturaInesperadaApareceNoDiagnosticoComErro() {
        var c = new ConstrutorDeExportacao().seguindo("ana");
        c.arquivo("connections/followers_and_following/followers_1.json", "\"isto não é uma lista\"");
        ResultadoDaImportacao r = importar(c);
        var d = r.diagnosticos().stream().filter(x -> x.categoria() == CategoriaDeDados.SEGUIDORES).findFirst().orElseThrow();
        assertThat(d.erro()).isNotNull();
        assertThat(r.categoriasIncompletas()).contains(CategoriaDeDados.SEGUIDORES);
    }

    @Test
    void formasDistintasSaoContadasELimitadas() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"campo_").append(i).append("\":1,\"string_list_data\":[{\"value\":\"u").append(i).append("\"}]}");
        }
        json.append("]");
        var c = new ConstrutorDeExportacao().seguindo("ana");
        c.arquivo("connections/followers_and_following/followers_1.json", json.toString());
        var d = importar(c).diagnosticos().stream().filter(x -> x.categoria() == CategoriaDeDados.SEGUIDORES).findFirst().orElseThrow();
        assertThat(d.formas().size()).isLessThanOrEqualTo(13);
        assertThat(d.formas()).containsKey("(outras formas)");
        assertThat(d.validos()).isEqualTo(30);
    }
}
