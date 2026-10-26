package br.com.instadino.importacao;

import br.com.instadino.importacao.ImportacaoRejeitadaException.Motivo;
import br.com.instadino.importacao.parser.LeitorDeRelacionamentos;
import br.com.instadino.importacao.parser.ResultadoDeLeitura;
import br.com.instadino.importacao.seguranca.EntradaLimitada;
import br.com.instadino.importacao.seguranca.EntradaLimitada.LimiteExcedidoException;
import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.nucleo.dominio.Relacionamento;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Orquestra a importação: inspeciona o ZIP, lê em memória apenas os arquivos das categorias suportadas e
 * autorizadas e devolve um {@link ResultadoDaImportacao}. Nada é extraído para o disco, nada é gravado no
 * banco e nenhum conteúdo é executado. A persistência é responsabilidade do chamador, após a confirmação.
 */
public final class ImportadorDeExportacao {

    public static final String VERSAO_DO_PARSER = "1.0.0";

    private static final Map<CategoriaDeDados, TipoDeRelacionamento> TIPOS = new EnumMap<>(CategoriaDeDados.class);

    static {
        TIPOS.put(CategoriaDeDados.SEGUIDORES, TipoDeRelacionamento.SEGUIDOR);
        TIPOS.put(CategoriaDeDados.SEGUINDO, TipoDeRelacionamento.SEGUINDO);
        TIPOS.put(CategoriaDeDados.SOLICITACOES_PENDENTES, TipoDeRelacionamento.SOLICITACAO_PENDENTE);
        TIPOS.put(CategoriaDeDados.BLOQUEADOS, TipoDeRelacionamento.BLOQUEADO);
        TIPOS.put(CategoriaDeDados.RESTRITOS, TipoDeRelacionamento.RESTRITO);
        TIPOS.put(CategoriaDeDados.FAVORITOS, TipoDeRelacionamento.FAVORITO);
    }

    private final LimitesDeImportacao limites;

    public ImportadorDeExportacao(LimitesDeImportacao limites) {
        this.limites = limites;
    }

    /** Etapas 2 a 5 do fluxo: valida o arquivo e gera o inventário de categorias. */
    public InventarioDoZip inspecionar(Path zip, Cancelamento cancelamento, OuvinteDeProgresso ouvinte) {
        return ValidadorDeZip.inspecionar(zip, limites, cancelamento, ouvinte);
    }

    /**
     * Etapas 7 a 9: processa os dados. O ZIP é inspecionado novamente aqui, para que o arquivo analisado seja
     * o mesmo que o confirmado pelo usuário (evita troca do arquivo entre a inspeção e a leitura).
     */
    public ResultadoDaImportacao importar(Path zip, ConfiguracaoDeImportacao configuracao, Cancelamento cancelamento,
                                          OuvinteDeProgresso ouvinte) {
        Cronometro inicio = new Cronometro();
        InventarioDoZip inventario = inspecionar(zip, cancelamento, ouvinte);

        List<EntradaInventariada> alvos = inventario.entradas().stream()
                .filter(e -> e.categoria().processadaNestaVersao() && configuracao.permite(e.categoria()))
                .filter(e -> e.formato() == FormatoDeArquivo.JSON || e.formato() == FormatoDeArquivo.HTML)
                .sorted(Comparator.comparing(EntradaInventariada::caminho))
                .toList();

        Map<TipoDeRelacionamento, Map<NomeDeUsuario, Long>> acumulado = new EnumMap<>(TipoDeRelacionamento.class);
        Set<CategoriaDeDados> processadas = EnumSet.noneOf(CategoriaDeDados.class);
        Set<CategoriaDeDados> incompletas = EnumSet.noneOf(CategoriaDeDados.class);
        Map<String, Integer> invalidosPorMotivo = new TreeMap<>();
        List<String> avisos = new ArrayList<>(inventario.avisos());
        int invalidos = 0;
        int duplicados = 0;
        long totalLido = 0;

        try (ZipFile arquivo = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            int numero = 0;
            for (EntradaInventariada alvo : alvos) {
                cancelamento.verificar();
                ValidadorDeZip.verificarTempo(inicio, limites);
                ouvinte.progresso("Lendo " + alvo.categoria().rotulo(), numero++, alvos.size());
                TipoDeRelacionamento tipo = TIPOS.get(alvo.categoria());
                ZipEntry entrada = arquivo.getEntry(alvo.nomeOriginal());
                if (entrada == null || tipo == null) {
                    incompletas.add(alvo.categoria());
                    avisos.add("Um arquivo de " + alvo.categoria().rotulo() + " não pôde ser localizado.");
                    continue;
                }
                long restante = limites.bytesLidosTotalMaximo() - totalLido;
                EntradaLimitada.Vigia vigia = () -> {
                    cancelamento.verificar();
                    ValidadorDeZip.verificarTempo(inicio, limites);
                };
                try (InputStream bruto = arquivo.getInputStream(entrada);
                     EntradaLimitada limitada = new EntradaLimitada(bruto,
                             Math.min(limites.tamanhoMaximoPorEntradaLida(), restante), vigia)) {
                    ResultadoDeLeitura leitura = alvo.formato() == FormatoDeArquivo.JSON
                            ? LeitorDeRelacionamentos.lerJson(limitada, cancelamento)
                            : LeitorDeRelacionamentos.lerHtml(limitada);
                    totalLido += limitada.bytesLidos();
                    Map<NomeDeUsuario, Long> destino = acumulado.computeIfAbsent(tipo, k -> new LinkedHashMap<>());
                    duplicados += leitura.duplicados();
                    for (Map.Entry<NomeDeUsuario, Long> u : leitura.usuarios().entrySet()) {
                        if (destino.containsKey(u.getKey())) {
                            duplicados++;
                        }
                        destino.merge(u.getKey(), u.getValue() == null ? Long.MAX_VALUE : u.getValue(), Math::min);
                    }
                    for (Map.Entry<String, Integer> m : leitura.invalidosPorMotivo().entrySet()) {
                        invalidosPorMotivo.merge(m.getKey(), m.getValue(), Integer::sum);
                        invalidos += m.getValue();
                    }
                    processadas.add(alvo.categoria());
                } catch (LimiteExcedidoException e) {
                    throw new ImportacaoRejeitadaException(Motivo.COMPRESSAO_SUSPEITA);
                } catch (ZipException e) {
                    if (e.getMessage() != null && e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("encrypted")) {
                        throw new ImportacaoRejeitadaException(Motivo.ENTRADA_CRIPTOGRAFADA);
                    }
                    incompletas.add(alvo.categoria());
                    avisos.add("Um arquivo de " + alvo.categoria().rotulo() + " está corrompido e foi ignorado.");
                } catch (IOException | RuntimeException e) {
                    if (e instanceof ImportacaoRejeitadaException rejeitada) {
                        throw rejeitada;
                    }
                    incompletas.add(alvo.categoria());
                    avisos.add("Um arquivo de " + alvo.categoria().rotulo() + " está ilegível ou incompleto e foi ignorado.");
                }
            }
        } catch (IOException e) {
            throw new ImportacaoRejeitadaException(Motivo.NAO_E_ZIP);
        }
        cancelamento.verificar();

        Map<TipoDeRelacionamento, List<Relacionamento>> relacionamentos = new EnumMap<>(TipoDeRelacionamento.class);
        int validos = 0;
        for (Map.Entry<TipoDeRelacionamento, Map<NomeDeUsuario, Long>> e : acumulado.entrySet()) {
            List<Relacionamento> lista = e.getValue().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(x -> new Relacionamento(e.getKey(), x.getKey(), x.getValue() == Long.MAX_VALUE ? null : x.getValue()))
                    .toList();
            relacionamentos.put(e.getKey(), lista);
            validos += lista.size();
        }
        if (invalidos > 0) {
            avisos.add(invalidos + " registro(s) sem nome de usuário válido foram descartados.");
        }
        for (CategoriaDeDados sensivel : inventario.categoriasSensiveisPresentes()) {
            if (!configuracao.permite(sensivel)) {
                avisos.add("A categoria \"" + sensivel.rotulo() + "\" está presente, mas não foi autorizada e não foi processada.");
            }
        }
        for (CategoriaDeDados c : incompletas) {
            avisos.add("A lista \"" + c.rotulo() + "\" pode estar incompleta; comparações que dependem dela devem ser interpretadas com cautela.");
        }
        return new ResultadoDaImportacao(VERSAO_DO_PARSER, inventario, relacionamentos, processadas, incompletas, validos,
                invalidos, duplicados, invalidosPorMotivo, avisos, inicio.decorrido());
    }
}
