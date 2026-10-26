package br.com.instadino.analise.exportacao;

import br.com.instadino.analise.social.AnaliseDeSeguidores;
import br.com.instadino.analise.social.ResumoSocial;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.nucleo.dominio.Relacionamento;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Exportações CSV do plano Free: listas simples de contas com o endereço do perfil e a data registrada. */
public final class RelatoriosCsvBasicos {

    private static final List<String> CABECALHO = List.of("usuario", "perfil", "data_registro_utc");

    private RelatoriosCsvBasicos() {
    }

    public static void listaDeRelacionamentos(OutputStream saida, Collection<Relacionamento> registros) throws IOException {
        Map<NomeDeUsuario, Relacionamento> porUsuario = registros.stream()
                .collect(Collectors.toMap(Relacionamento::usuario, Function.identity(), (a, b) -> a));
        List<List<?>> linhas = new ArrayList<>();
        porUsuario.values().stream().sorted(java.util.Comparator.comparing(Relacionamento::usuario)).forEach(r -> linhas.add(List.of(
                r.usuario().valor(), r.usuario().urlDoPerfil(),
                r.instanteEmSegundos() == null ? "" : Instant.ofEpochSecond(r.instanteEmSegundos()).toString())));
        ExportadorCsv.escrever(saida, CABECALHO, linhas);
    }

    public static void contas(OutputStream saida, Collection<NomeDeUsuario> contas) throws IOException {
        List<List<?>> linhas = contas.stream().sorted()
                .<List<?>>map(u -> List.of(u.valor(), u.urlDoPerfil(), "")).toList();
        ExportadorCsv.escrever(saida, CABECALHO, linhas);
    }

    public static void mutuos(OutputStream saida, ResumoSocial resumo) throws IOException {
        contas(saida, resumo.mutuos());
    }

    /** Contas seguidas que não aparecem na lista de seguidores. A limitação dos dados acompanha o arquivo. */
    public static void naoSeguemDeVolta(OutputStream saida, ResumoSocial resumo) throws IOException {
        List<List<?>> linhas = new ArrayList<>();
        resumo.naoSeguemDeVolta().stream().sorted().forEach(u -> linhas.add(List.of(u.valor(), u.urlDoPerfil(), "")));
        linhas.add(List.of("", "", ""));
        linhas.add(List.of("Observação", AnaliseDeSeguidores.LIMITACAO, ""));
        ExportadorCsv.escrever(saida, CABECALHO, linhas);
    }
}
