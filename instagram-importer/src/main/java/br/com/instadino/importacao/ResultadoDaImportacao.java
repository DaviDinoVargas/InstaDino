package br.com.instadino.importacao;

import br.com.instadino.nucleo.dominio.Relacionamento;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Resultado em memória de uma importação, antes de qualquer persistência. O usuário revisa o resumo e só então
 * os dados são gravados.
 *
 * @param registrosValidos contas únicas aceitas, somando todas as listas
 * @param registrosInvalidos registros descartados por não conterem nome de usuário válido
 * @param registrosDuplicados repetições descartadas dentro de uma mesma lista
 */
public record ResultadoDaImportacao(
        String versaoDoParser,
        InventarioDoZip inventario,
        Map<TipoDeRelacionamento, List<Relacionamento>> relacionamentos,
        Set<CategoriaDeDados> categoriasProcessadas,
        Set<CategoriaDeDados> categoriasIncompletas,
        int registrosValidos,
        int registrosInvalidos,
        int registrosDuplicados,
        Map<String, Integer> invalidosPorMotivo,
        List<String> avisos,
        Duration duracao,
        List<DiagnosticoDeArquivo> diagnosticos,
        List<String> avisosDeCobertura,
        List<RegistroDeAtividade> atividades,
        List<Publicacao> publicacoes) {

    public ResultadoDaImportacao {
        relacionamentos = copiar(relacionamentos);
        categoriasProcessadas = categoriasProcessadas.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(categoriasProcessadas));
        categoriasIncompletas = categoriasIncompletas.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(categoriasIncompletas));
        invalidosPorMotivo = Map.copyOf(new TreeMap<>(invalidosPorMotivo));
        avisos = List.copyOf(avisos);
        diagnosticos = List.copyOf(diagnosticos);
        avisosDeCobertura = List.copyOf(avisosDeCobertura);
        atividades = List.copyOf(atividades);
        publicacoes = List.copyOf(publicacoes);
    }

    private static Map<TipoDeRelacionamento, List<Relacionamento>> copiar(Map<TipoDeRelacionamento, List<Relacionamento>> origem) {
        Map<TipoDeRelacionamento, List<Relacionamento>> mapa = new EnumMap<>(TipoDeRelacionamento.class);
        origem.forEach((k, v) -> mapa.put(k, List.copyOf(v)));
        return java.util.Collections.unmodifiableMap(mapa);
    }

    public List<Relacionamento> lista(TipoDeRelacionamento tipo) {
        return relacionamentos.getOrDefault(tipo, List.of());
    }

    /** As duas listas necessárias para a comparação de seguidores foram lidas por completo. */
    public boolean permiteComparacaoDeSeguidores() {
        return categoriasProcessadas.contains(CategoriaDeDados.SEGUIDORES)
                && categoriasProcessadas.contains(CategoriaDeDados.SEGUINDO)
                && !categoriasIncompletas.contains(CategoriaDeDados.SEGUIDORES)
                && !categoriasIncompletas.contains(CategoriaDeDados.SEGUINDO);
    }
}
