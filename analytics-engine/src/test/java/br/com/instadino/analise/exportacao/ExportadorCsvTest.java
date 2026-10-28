package br.com.instadino.analise.exportacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.analise.social.AnaliseDeSeguidores;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.nucleo.dominio.Relacionamento;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExportadorCsvTest {

    private static String gerar(List<String> cab, List<List<?>> linhas) throws IOException {
        var saida = new ByteArrayOutputStream();
        ExportadorCsv.escrever(saida, cab, linhas);
        return new String(saida.toByteArray(), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+cmd|' /C calc'!A0", "-2+3", "@SUM(A1)", "\tcmd", "\rcmd", "\t=1+1", "  =1+1", "\u0001=HYPERLINK(\"http://evil\")"})
    void neutralizaFormulasDePlanilha(String perigoso) {
        String celula = ExportadorCsv.formatar(perigoso);
        assertThat(celula.replace("\"", "")).startsWith("'");
    }

    @Test
    void numerosNegativosLegitimosNaoSaoAlterados() {
        assertThat(ExportadorCsv.formatar(-5)).isEqualTo("-5");
        assertThat(ExportadorCsv.formatar(3.5)).isEqualTo("3.5");
    }

    @Test
    void escapaSeparadorAspasEQuebrasDeLinha() {
        assertThat(ExportadorCsv.formatar("a;b")).isEqualTo("\"a;b\"");
        assertThat(ExportadorCsv.formatar("diz \"oi\"")).isEqualTo("\"diz \"\"oi\"\"\"");
        assertThat(ExportadorCsv.formatar("linha1\nlinha2")).isEqualTo("\"linha1\nlinha2\"");
    }

    @Test
    void removeCaracteresDeControle() {
        assertThat(ExportadorCsv.formatar("a\u0000b\u0007c\u001b")).isEqualTo("abc");
    }

    @Test
    void preservaAcentuacaoENomesDeArquivoUnicode() throws IOException {
        String csv = gerar(List.of("nome", "cidade"), List.of(List.of("José da Conceição", "São Paulo — ação")));
        assertThat(csv).startsWith("﻿nome;cidade\r\n").contains("José da Conceição;São Paulo — ação\r\n");
    }

    @Test
    void celulasNulasViramVazias() throws IOException {
        var linha = new java.util.ArrayList<Object>();
        linha.add("x");
        linha.add(null);
        assertThat(gerar(List.of("a", "b"), List.of(linha))).contains("x;\r\n");
    }

    @Test
    void exportaListaDeRelacionamentosOrdenadaComDataIsoUtc() throws IOException {
        var registros = List.of(
                new Relacionamento(TipoDeRelacionamento.SEGUIDOR, new NomeDeUsuario("bruno"), 1704067200L),
                new Relacionamento(TipoDeRelacionamento.SEGUIDOR, new NomeDeUsuario("ana"), null));
        var saida = new ByteArrayOutputStream();
        RelatoriosCsvBasicos.listaDeRelacionamentos(saida, registros);
        String csv = new String(saida.toByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("usuario;perfil;data_registro_utc")
                .contains("ana;https://www.instagram.com/ana/;\r\n")
                .contains("bruno;https://www.instagram.com/bruno/;2024-01-01T00:00:00Z");
        assertThat(csv.indexOf("ana;")).isLessThan(csv.indexOf("bruno;"));
    }

    @Test
    void exportacaoDeNaoRecíprocosIncluiALimitacaoDosDados() throws IOException {
        var resumo = AnaliseDeSeguidores.analisar(List.of(new NomeDeUsuario("ana")), List.of(new NomeDeUsuario("bruno")));
        var saida = new ByteArrayOutputStream();
        RelatoriosCsvBasicos.naoSeguemDeVolta(saida, resumo);
        String csv = new String(saida.toByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("bruno;").contains("não prova");
    }

    @Test
    void volumeGrandeEGeradoSemEstourarMemoria() throws IOException {
        var contas = new java.util.ArrayList<NomeDeUsuario>();
        for (int i = 0; i < 100_000; i++) {
            contas.add(new NomeDeUsuario("usuario" + i));
        }
        var saida = new ByteArrayOutputStream();
        RelatoriosCsvBasicos.contas(saida, contas);
        assertThat(saida.size()).isGreaterThan(100_000 * 20);
    }
}
