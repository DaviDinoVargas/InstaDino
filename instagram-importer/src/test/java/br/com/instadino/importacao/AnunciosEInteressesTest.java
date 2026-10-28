package br.com.instadino.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnunciosEInteressesTest {

    @TempDir
    Path pasta;

    private List<RegistroDeAtividade> importar(ConstrutorDeExportacao c) {
        Path zip = c.escreverEm(pasta.resolve("e.zip"));
        return new ImportadorDeExportacao(LimitesDeImportacao.padrao()).importar(zip, ConfiguracaoDeImportacao.padrao(), new Cancelamento(), OuvinteDeProgresso.NENHUM)
                .atividades().stream().filter(a -> a.categoria() == CategoriaDeDados.ANUNCIOS_E_PREFERENCIAS).toList();
    }

    private ConstrutorDeExportacao base() {
        return new ConstrutorDeExportacao().seguidores("ana").seguindo("ana");
    }

    @Test
    void leTemasInferidosPorRotuloEmPortuguesEIngles() {
        var c = base().arquivo("conta/ads_information/instagram_ads_and_businesses/your_topics.json", """
                {"topics_your_topics":[
                  {"timestamp_ms":1700000000000,"string_map_data":{"Name":{"value":"Fitness"}}},
                  {"string_map_data":{"Nome":{"value":"Viagens e Turismo"}}},
                  {"string_map_data":{"Nome":{"value":"Cat\\u00c3\\u00a1logos"}}}]}""");
        var itens = importar(c);
        assertThat(itens).extracting(RegistroDeAtividade::texto).contains("Fitness", "Viagens e Turismo");
        assertThat(itens).allSatisfy(a -> assertThat(a.subtipo()).isEqualTo("your_topics"));
        assertThat(itens.get(0).instanteEmSegundos()).isEqualTo(1700000000L);
    }

    @Test
    void leAnunciantesEmFormatoPlano() {
        var c = base().arquivo("conta/ads_information/instagram_ads_and_businesses/advertisers_using_your_activity_or_information.json", """
                {"ig_custom_audiences_all_types":[{"advertiser_name":"Loja Exemplo","has_data_file_custom_audience":true},
                                                  {"advertiser_name":"Outra Marca","has_remarketing_custom_audience":false}]}""");
        var itens = importar(c);
        assertThat(itens).extracting(RegistroDeAtividade::texto).containsExactly("Loja Exemplo", "Outra Marca");
        assertThat(itens).allSatisfy(a -> assertThat(a.conta()).isNull());
    }

    @Test
    void anunciosVistosGuardamOAutorQuandoHaRotuloDeAutor() {
        var c = base().arquivo("conta/ads_information/ads_and_topics/ads_viewed.json", """
                {"impressions_history_ads_seen":[{"string_map_data":{"Author":{"value":"marca_x"},"Time":{"timestamp":1700000100}}}]}""");
        var itens = importar(c);
        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).conta()).isEqualTo("marca_x");
        assertThat(itens.get(0).instanteEmSegundos()).isEqualTo(1700000100L);
    }

    @Test
    void semAtividadeAutorizadaNadaEhLido() {
        var c = base().arquivo("conta/ads_information/instagram_ads_and_businesses/your_topics.json",
                "{\"topics_your_topics\":[{\"string_map_data\":{\"Name\":{\"value\":\"Fitness\"}}}]}");
        Path zip = c.escreverEm(pasta.resolve("e.zip"));
        var r = new ImportadorDeExportacao(LimitesDeImportacao.padrao()).importar(zip, ConfiguracaoDeImportacao.padrao().semAtividadeNemConteudo(),
                new Cancelamento(), OuvinteDeProgresso.NENHUM);
        assertThat(r.atividades()).isEmpty();
    }
}
