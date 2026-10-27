package br.com.instadino.servicos;

import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.importacao.ConfiguracaoDeImportacao;
import br.com.instadino.importacao.ImportadorDeExportacao;
import br.com.instadino.importacao.InventarioDoZip;
import br.com.instadino.importacao.LimitesDeImportacao;
import br.com.instadino.importacao.OuvinteDeProgresso;
import br.com.instadino.importacao.ResultadoDaImportacao;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.nucleo.dominio.Relacionamento;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.NovaImportacao;
import br.com.instadino.persistencia.repositorio.RepositorioDePerfis;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluxo de importação: inspecionar, processar, revisar o resumo e só então persistir. Persistir exige confirmação
 * explícita do chamador (passo 10 do fluxo); nada é gravado antes disso.
 */
public final class ServicoDeImportacao {

    /** Importações mantidas no plano Free (a mais recente). O Premium mantém o histórico completo. */
    public static final int RETENCAO_FREE = 1;

    private final LimitesDeImportacao limites;
    private final PoliticaDeAcesso politica;
    private final RepositorioDeImportacoes importacoes;
    private final RepositorioDePerfis perfis;
    private final RegistroDeAuditoriaLocal auditoria;
    private final Runnable aoAlterarDados;

    ServicoDeImportacao(LimitesDeImportacao limites, PoliticaDeAcesso politica, RepositorioDeImportacoes importacoes,
                        RepositorioDePerfis perfis, RegistroDeAuditoriaLocal auditoria, Runnable aoAlterarDados) {
        this.limites = limites;
        this.politica = politica;
        this.importacoes = importacoes;
        this.perfis = perfis;
        this.auditoria = auditoria;
        this.aoAlterarDados = aoAlterarDados;
    }

    public InventarioDoZip inspecionar(Path zip, Cancelamento cancelamento, OuvinteDeProgresso ouvinte) {
        return new ImportadorDeExportacao(limites).inspecionar(zip, cancelamento, ouvinte);
    }

    public ResultadoDaImportacao processar(Path zip, ConfiguracaoDeImportacao configuracao, Cancelamento cancelamento,
                                           OuvinteDeProgresso ouvinte) {
        politica.exigir(Funcionalidade.IMPORTACAO_ZIP);
        ConfiguracaoDeImportacao efetiva = autorizadas(configuracao);
        return new ImportadorDeExportacao(limites).importar(zip, efetiva, cancelamento, ouvinte);
    }

    /** Grava o resultado já revisado pelo usuário. Devolve o identificador da importação. */
    public String confirmar(ResultadoDaImportacao resultado, long perfilId, String nomeDoArquivo, Long tamanhoEmBytes) {
        politica.exigir(Funcionalidade.IMPORTACAO_ZIP);
        if (perfis.obter(perfilId).isEmpty()) {
            throw new IllegalArgumentException("Perfil inexistente.");
        }
        List<Relacionamento> todos = new ArrayList<>();
        resultado.relacionamentos().values().forEach(todos::addAll);

        Map<String, int[]> categorias = new LinkedHashMap<>();
        Map<CategoriaDeDados, Integer> arquivos = resultado.inventario().arquivosPorCategoria();
        arquivos.forEach((categoria, quantidade) -> categorias.put(categoria.name(), new int[] {quantidade, 0}));
        resultado.relacionamentos().forEach((tipo, lista) -> {
            CategoriaDeDados categoria = categoriaDoTipo(tipo);
            categorias.computeIfAbsent(categoria.name(), k -> new int[] {0, 0})[1] += lista.size();
        });

        resultado.atividades().stream().collect(java.util.stream.Collectors.groupingBy(a -> a.categoria().name(), java.util.stream.Collectors.counting()))
                .forEach((categoria, quantidade) -> categorias.computeIfAbsent(categoria, k -> new int[] {0, 0})[1] += quantidade.intValue());
        resultado.publicacoes().stream().collect(java.util.stream.Collectors.groupingBy(x -> x.categoria().name(), java.util.stream.Collectors.counting()))
                .forEach((categoria, quantidade) -> categorias.computeIfAbsent(categoria, k -> new int[] {0, 0})[1] += quantidade.intValue());
        List<RepositorioDeImportacoes.AtividadeGravada> atividades = resultado.atividades().stream()
                .map(a -> new RepositorioDeImportacoes.AtividadeGravada(a.categoria().name(), a.subtipo(), a.conta(), a.texto(), a.referencia(), a.instanteEmSegundos()))
                .toList();
        List<RepositorioDeImportacoes.PublicacaoGravada> publicacoes = resultado.publicacoes().stream()
                .map(x -> new RepositorioDeImportacoes.PublicacaoGravada(x.categoria().name(), x.legenda(), x.instanteEmSegundos(),
                        x.midias().stream().map(m -> new RepositorioDeImportacoes.MidiaGravada(m.caminho(), m.tipo().name(), m.tamanhoEmBytes())).toList()))
                .toList();
        int retencao = politica.permite(Funcionalidade.HISTORICO_COMPLETO) ? 0 : RETENCAO_FREE;
        String id = importacoes.salvar(new NovaImportacao(perfilId, resultado.versaoDoParser(), "Exportação oficial (ZIP)",
                nomeDoArquivo, tamanhoEmBytes, resultado.registrosValidos(), resultado.registrosInvalidos(), categorias, todos, resumoJson(resultado), atividades, publicacoes), retencao);
        auditoria.registrar("IMPORTACAO_CONCLUIDA", resultado.registrosValidos() + " registros válidos");
        aoAlterarDados.run();
        return id;
    }

    /** Guarda quais categorias vieram incompletas, para que comparações futuras avaliem a confiabilidade. */
    static String resumoJson(ResultadoDaImportacao resultado) {
        try {
            Map<String, Object> resumo = new LinkedHashMap<>();
            resumo.put("incompletas", resultado.categoriasIncompletas().stream().map(Enum::name).sorted().toList());
            resumo.put("avisos", resultado.avisos().size());
            resumo.put("cobertura", resultado.avisosDeCobertura());
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(resumo);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    private ConfiguracaoDeImportacao autorizadas(ConfiguracaoDeImportacao pedida) {
        // Categorias sensíveis só são processadas se a política de acesso do módulo também permitir.
        var permitidas = pedida.categoriasSensiveisAutorizadas().stream()
                .filter(c -> c != CategoriaDeDados.MENSAGENS || politica.permite(Funcionalidade.ARQUIVO_DE_MENSAGENS))
                .collect(java.util.stream.Collectors.toSet());
        // Atividade e conteúdo são recursos Premium: sem o plano eles nem são lidos nem guardados.
        return new ConfiguracaoDeImportacao(permitidas, politica.permite(Funcionalidade.ANALISE_DE_ATIVIDADE),
                politica.permite(Funcionalidade.BIBLIOTECA_DE_CONTEUDO));
    }

    private static CategoriaDeDados categoriaDoTipo(br.com.instadino.nucleo.dominio.TipoDeRelacionamento tipo) {
        return switch (tipo) {
            case SEGUIDOR -> CategoriaDeDados.SEGUIDORES;
            case SEGUINDO -> CategoriaDeDados.SEGUINDO;
            case SOLICITACAO_PENDENTE -> CategoriaDeDados.SOLICITACOES_PENDENTES;
            case BLOQUEADO -> CategoriaDeDados.BLOQUEADOS;
            case RESTRITO -> CategoriaDeDados.RESTRITOS;
            case FAVORITO -> CategoriaDeDados.FAVORITOS;
            case DEIXEI_DE_SEGUIR_RECENTE -> CategoriaDeDados.DEIXEI_DE_SEGUIR;
            case SOLICITACAO_RECENTE -> CategoriaDeDados.SOLICITACOES_RECENTES;
            case SOLICITACAO_RECEBIDA -> CategoriaDeDados.SOLICITACOES_RECEBIDAS;
        };
    }
}
