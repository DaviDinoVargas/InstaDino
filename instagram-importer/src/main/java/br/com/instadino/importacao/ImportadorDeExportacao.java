package br.com.instadino.importacao;

import br.com.instadino.importacao.ImportacaoRejeitadaException.Motivo;
import br.com.instadino.importacao.parser.LeitorDeAtividade;
import br.com.instadino.importacao.parser.LeitorDeMensagens;
import br.com.instadino.importacao.parser.LeitorDePublicacoes;
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

    public static final String VERSAO_DO_PARSER = "1.6.0";

    /** Categorias de atividade lidas por {@link LeitorDeAtividade}. */
    public static final Set<CategoriaDeDados> ATIVIDADES = java.util.Collections.unmodifiableSet(EnumSet.of(CategoriaDeDados.CURTIDAS,
            CategoriaDeDados.COMENTARIOS, CategoriaDeDados.SALVOS, CategoriaDeDados.PESQUISAS, CategoriaDeDados.INTERACOES_STORIES,
            CategoriaDeDados.VISUALIZACOES, CategoriaDeDados.ANUNCIOS_E_PREFERENCIAS));
    /** Categorias de conteúdo lidas por {@link LeitorDePublicacoes}. */
    public static final Set<CategoriaDeDados> CONTEUDOS = java.util.Collections.unmodifiableSet(EnumSet.of(CategoriaDeDados.PUBLICACOES,
            CategoriaDeDados.STORIES, CategoriaDeDados.REELS));
    /** Limites de volume por importação, para que uma exportação enorme não esgote a memória. */
    public static final int MAXIMO_DE_REGISTROS_DE_ATIVIDADE = 1_000_000;
    public static final int MAXIMO_DE_PUBLICACOES = 200_000;

    private static final java.util.regex.Pattern ARQUIVO_DE_MENSAGENS = java.util.regex.Pattern.compile("(^|/)message_\\d+\\.json$");

    private static final Map<CategoriaDeDados, TipoDeRelacionamento> TIPOS = new EnumMap<>(CategoriaDeDados.class);

    static {
        TIPOS.put(CategoriaDeDados.SEGUIDORES, TipoDeRelacionamento.SEGUIDOR);
        TIPOS.put(CategoriaDeDados.SEGUINDO, TipoDeRelacionamento.SEGUINDO);
        TIPOS.put(CategoriaDeDados.SOLICITACOES_PENDENTES, TipoDeRelacionamento.SOLICITACAO_PENDENTE);
        TIPOS.put(CategoriaDeDados.BLOQUEADOS, TipoDeRelacionamento.BLOQUEADO);
        TIPOS.put(CategoriaDeDados.RESTRITOS, TipoDeRelacionamento.RESTRITO);
        TIPOS.put(CategoriaDeDados.FAVORITOS, TipoDeRelacionamento.FAVORITO);
        TIPOS.put(CategoriaDeDados.DEIXEI_DE_SEGUIR, TipoDeRelacionamento.DEIXEI_DE_SEGUIR_RECENTE);
        TIPOS.put(CategoriaDeDados.SOLICITACOES_RECENTES, TipoDeRelacionamento.SOLICITACAO_RECENTE);
        TIPOS.put(CategoriaDeDados.SOLICITACOES_RECEBIDAS, TipoDeRelacionamento.SOLICITACAO_RECEBIDA);
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
                .filter(e -> e.categoria() != CategoriaDeDados.MENSAGENS
                        || ARQUIVO_DE_MENSAGENS.matcher(e.caminho().toLowerCase(java.util.Locale.ROOT)).find())
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
        List<DiagnosticoDeArquivo> diagnosticos = new ArrayList<>();
        List<RegistroDeAtividade> atividades = new ArrayList<>();
        List<Publicacao> publicacoes = new ArrayList<>();
        java.util.function.Function<String, Publicacao.MidiaDePublicacao> resolvedorDeMidia = resolvedorDeMidia(inventario);
        AcumuladorDeConversas conversas = new AcumuladorDeConversas();
        TotaisDeMensagens totaisDeMensagens = new TotaisDeMensagens();

        try (ZipFile arquivo = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            int numero = 0;
            for (EntradaInventariada alvo : alvos) {
                cancelamento.verificar();
                ValidadorDeZip.verificarTempo(inicio, limites);
                ouvinte.progresso("Lendo " + alvo.categoria().rotulo(), numero++, alvos.size());
                TipoDeRelacionamento tipo = TIPOS.get(alvo.categoria());
                ZipEntry entrada = arquivo.getEntry(alvo.nomeOriginal());
                boolean ehAtividade = ATIVIDADES.contains(alvo.categoria());
                boolean ehConteudo = CONTEUDOS.contains(alvo.categoria());
                boolean ehMensagem = alvo.categoria() == CategoriaDeDados.MENSAGENS;
                if (entrada == null || (tipo == null && !ehAtividade && !ehConteudo && !ehMensagem)) {
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
                    if (tipo == null) {
                        if (alvo.formato() != FormatoDeArquivo.JSON) {
                            incompletas.add(alvo.categoria());
                            diagnosticos.add(erroDeArquivo(alvo, "formato HTML não suportado para esta categoria; exporte em JSON"));
                            avisos.add("Arquivos de " + alvo.categoria().rotulo() + " em HTML não são lidos. Peça a exportação em JSON.");
                            continue;
                        }
                        if (ehMensagem) {
                            var r = LeitorDeMensagens.ler(limitada, cancelamento);
                            totalLido += limitada.bytesLidos();
                            totaisDeMensagens.somar(limitada.bytesLidos(), r.contagem());
                            String caminho = alvo.caminho();
                            if (!conversas.adicionar(caminho.substring(0, Math.max(caminho.lastIndexOf('/'), 0)), r)) {
                                incompletas.add(alvo.categoria());
                            }
                            invalidos += r.contagem().rejeitadosPorMotivo().values().stream().mapToInt(Integer::intValue).sum();
                        } else if (ehAtividade) {
                            var r = LeitorDeAtividade.ler(limitada, alvo.categoria(), subtipo(alvo.caminho()), cancelamento);
                            totalLido += limitada.bytesLidos();
                            registrarDiagnostico(diagnosticos, alvo, limitada.bytesLidos(), r.contagem());
                            int espaco = MAXIMO_DE_REGISTROS_DE_ATIVIDADE - atividades.size();
                            atividades.addAll(r.registros().subList(0, Math.min(espaco, r.registros().size())));
                            if (r.registros().size() > espaco) {
                                incompletas.add(alvo.categoria());
                                avisos.add("Há registros de atividade demais; foram mantidos os primeiros " + MAXIMO_DE_REGISTROS_DE_ATIVIDADE + ".");
                            }
                            invalidos += r.contagem().rejeitadosPorMotivo().values().stream().mapToInt(Integer::intValue).sum();
                        } else {
                            var r = LeitorDePublicacoes.ler(limitada, alvo.categoria(), cancelamento, resolvedorDeMidia);
                            totalLido += limitada.bytesLidos();
                            registrarDiagnostico(diagnosticos, alvo, limitada.bytesLidos(), r.contagem());
                            int espaco = MAXIMO_DE_PUBLICACOES - publicacoes.size();
                            publicacoes.addAll(r.publicacoes().subList(0, Math.min(espaco, r.publicacoes().size())));
                            if (r.publicacoes().size() > espaco) {
                                incompletas.add(alvo.categoria());
                                avisos.add("Há publicações demais; foram mantidas as primeiras " + MAXIMO_DE_PUBLICACOES + ".");
                            }
                            invalidos += r.contagem().rejeitadosPorMotivo().values().stream().mapToInt(Integer::intValue).sum();
                        }
                        processadas.add(alvo.categoria());
                        continue;
                    }
                    ResultadoDeLeitura leitura = alvo.formato() == FormatoDeArquivo.JSON
                            ? LeitorDeRelacionamentos.lerJson(limitada, cancelamento)
                            : LeitorDeRelacionamentos.lerHtml(limitada);
                    totalLido += limitada.bytesLidos();
                    diagnosticos.add(new DiagnosticoDeArquivo(DiagnosticoDeArquivo.caminhoSemRaiz(alvo.caminho()), alvo.categoria(),
                            limitada.bytesLidos(), leitura.registrosLidos(), leitura.usuarios().size(), leitura.duplicados(),
                            leitura.invalidosPorMotivo(), leitura.formas(), leitura.primeiroInstante(), leitura.ultimoInstante(), null));
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
                    diagnosticos.add(erroDeArquivo(alvo, "arquivo corrompido"));
                    avisos.add("Um arquivo de " + alvo.categoria().rotulo() + " está corrompido e foi ignorado.");
                } catch (IOException | RuntimeException e) {
                    if (e instanceof ImportacaoRejeitadaException rejeitada) {
                        throw rejeitada;
                    }
                    incompletas.add(alvo.categoria());
                    diagnosticos.add(erroDeArquivo(alvo, e instanceof IOException ? "estrutura JSON/HTML inesperada ou truncada" : "falha ao interpretar"));
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
        totaisDeMensagens.diagnostico().ifPresent(diagnosticos::add);
        List<ResumoDeConversa> resumosDeConversas = conversas.finalizar();
        if (conversas.limiteAtingido()) {
            avisos.add("Há conversas demais; foram mantidas as primeiras " + AcumuladorDeConversas.MAXIMO_DE_CONVERSAS + ".");
        }
        if (!resumosDeConversas.isEmpty() && !resumosDeConversas.get(0).titularIdentificado()) {
            avisos.add("Não foi possível identificar qual participante é você (há uma única conversa); as contagens de mensagens enviadas e recebidas ficam indisponíveis.");
        }
        if (invalidos > 0) {
            avisos.add(invalidos + " registro(s) sem nome de usuário válido foram descartados.");
        }
        for (CategoriaDeDados sensivel : inventario.categoriasSensiveisPresentes()) {
            if (!configuracao.permite(sensivel)) {
                avisos.add("A categoria \"" + sensivel.rotulo() + "\" está presente, mas não foi autorizada e não foi processada.");
            }
        }
        List<String> cobertura = new ArrayList<>();
        for (CategoriaDeDados parcial : List.of(CategoriaDeDados.SEGUIDORES, CategoriaDeDados.SEGUINDO)) {
            CategoriaDeDados outra = parcial == CategoriaDeDados.SEGUIDORES ? CategoriaDeDados.SEGUINDO : CategoriaDeDados.SEGUIDORES;
            avaliarCobertura(diagnosticos, parcial, outra).ifPresent(texto -> {
                cobertura.add(texto);
                avisos.add(texto);
                incompletas.add(parcial);
            });
        }
        for (CategoriaDeDados c : incompletas) {
            avisos.add("A lista \"" + c.rotulo() + "\" pode estar incompleta; comparações que dependem dela devem ser interpretadas com cautela.");
        }
        return new ResultadoDaImportacao(VERSAO_DO_PARSER, inventario, relacionamentos, processadas, incompletas, validos,
                invalidos, duplicados, invalidosPorMotivo, avisos, inicio.decorrido(), diagnosticos, cobertura, atividades, publicacoes, resumosDeConversas);
    }

    private static DiagnosticoDeArquivo erroDeArquivo(EntradaInventariada alvo, String motivo) {
        return new DiagnosticoDeArquivo(DiagnosticoDeArquivo.caminhoSemRaiz(alvo.caminho()), alvo.categoria(), alvo.tamanhoDeclarado(),
                0, 0, 0, Map.of(), Map.of(), null, null, motivo);
    }

    private static final long DIA = 86_400L;
    private static final java.time.format.DateTimeFormatter DATA_BR = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Detecta a assinatura de uma exportação feita com intervalo de datas: a lista {@code parcial} cobre só cerca de um
     * ano que termina na data da própria exportação, enquanto a lista {@code outra} cobre pelo menos seis meses a mais.
     * É um indício, não uma prova: por isso o texto fala em "possivelmente" e a lista é marcada como incompleta.
     */
    static java.util.Optional<String> avaliarCobertura(List<DiagnosticoDeArquivo> diagnosticos, CategoriaDeDados parcial, CategoriaDeDados outra) {
        Long minParcial = menor(diagnosticos, parcial);
        Long minOutra = menor(diagnosticos, outra);
        Long fim = diagnosticos.stream().map(DiagnosticoDeArquivo::ultimoInstante).filter(java.util.Objects::nonNull).max(Long::compare).orElse(null);
        if (minParcial == null || minOutra == null || fim == null) {
            return java.util.Optional.empty();
        }
        long janela = fim - minParcial;
        boolean periodoDeUmAno = janela >= 300 * DIA && janela <= 400 * DIA;
        boolean outraVaiMuitoAntes = minParcial - minOutra > 180 * DIA;
        if (!periodoDeUmAno || !outraVaiMuitoAntes) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of("A lista de " + parcial.rotulo().toLowerCase(java.util.Locale.ROOT) + " cobre apenas de " + data(minParcial) + " a "
                + data(fim) + ", enquanto a de " + outra.rotulo().toLowerCase(java.util.Locale.ROOT) + " cobre desde " + data(minOutra)
                + ". Isso costuma acontecer quando a exportação é pedida com um intervalo de datas (por exemplo, \"último ano\"). "
                + "Esta lista está possivelmente incompleta, então totais, mútuos e não recíprocos podem estar subestimados. "
                + "Peça ao Instagram uma nova exportação que inclua todo o período.");
    }

    private static Long menor(List<DiagnosticoDeArquivo> diagnosticos, CategoriaDeDados categoria) {
        return diagnosticos.stream().filter(d -> d.categoria() == categoria).map(DiagnosticoDeArquivo::primeiroInstante)
                .filter(java.util.Objects::nonNull).min(Long::compare).orElse(null);
    }

    private static String data(long segundos) {
        return DATA_BR.format(java.time.LocalDate.ofInstant(java.time.Instant.ofEpochSecond(segundos), java.time.ZoneOffset.UTC));
    }

    private static String subtipo(String caminho) {
        String nome = caminho.substring(caminho.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.ROOT);
        int ponto = nome.lastIndexOf('.');
        nome = ponto < 0 ? nome : nome.substring(0, ponto);
        return nome.replaceAll("_\\d+$", "");
    }

    private static void registrarDiagnostico(List<DiagnosticoDeArquivo> diagnosticos, EntradaInventariada alvo, long bytes,
                                             br.com.instadino.importacao.parser.ContagemDeLeitura c) {
        diagnosticos.add(new DiagnosticoDeArquivo(DiagnosticoDeArquivo.caminhoSemRaiz(alvo.caminho()), alvo.categoria(), bytes, c.elementos(),
                c.aceitos(), 0, c.rejeitadosPorMotivo(), c.formas(), c.primeiroInstante(), c.ultimoInstante(), null));
    }

    /** Resolve o caminho declarado de uma mídia contra o inventário (por sufixo de caminho), sem abrir nenhum arquivo. */
    private static java.util.function.Function<String, Publicacao.MidiaDePublicacao> resolvedorDeMidia(InventarioDoZip inventario) {
        Map<String, List<EntradaInventariada>> porNome = new java.util.HashMap<>();
        for (EntradaInventariada e : inventario.entradas()) {
            if (e.categoria() == CategoriaDeDados.MIDIA) {
                String nome = e.caminho().substring(e.caminho().lastIndexOf('/') + 1).toLowerCase(java.util.Locale.ROOT);
                porNome.computeIfAbsent(nome, k -> new ArrayList<>(1)).add(e);
            }
        }
        return uri -> {
            String u = uri.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            while (u.startsWith("./") || u.startsWith("/")) {
                u = u.startsWith("./") ? u.substring(2) : u.substring(1);
            }
            String nome = u.substring(u.lastIndexOf('/') + 1);
            for (EntradaInventariada e : porNome.getOrDefault(nome, List.of())) {
                String c = e.caminho().toLowerCase(java.util.Locale.ROOT);
                if (c.equals(u) || c.endsWith("/" + u)) {
                    return new Publicacao.MidiaDePublicacao(DiagnosticoDeArquivo.caminhoSemRaiz(e.caminho()),
                            Publicacao.TipoDeMidia.deCaminho(e.caminho()), e.tamanhoDeclarado());
                }
            }
            return null;
        };
    }
}
