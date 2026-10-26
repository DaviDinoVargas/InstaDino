package br.com.instadino.importacao;

import br.com.instadino.importacao.ImportacaoRejeitadaException.Motivo;
import br.com.instadino.importacao.seguranca.CaminhoSeguro;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Inspeciona o ZIP sem extrair nada: valida o arquivo, os limites, os caminhos e as duplicatas, e produz o
 * inventário de categorias. Nenhuma entrada é descompactada nesta etapa.
 */
public final class ValidadorDeZip {

    private ValidadorDeZip() {
    }

    public static InventarioDoZip inspecionar(Path zip, LimitesDeImportacao limites, Cancelamento cancelamento, OuvinteDeProgresso ouvinte) {
        Instant inicio = Instant.now();
        long tamanho = tamanhoValidado(zip, limites);

        List<EntradaInventariada> entradas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        long somaDeclarada = 0;

        try (ZipFile arquivo = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            int total = arquivo.size();
            if (total == 0) {
                throw new ImportacaoRejeitadaException(Motivo.ZIP_VAZIO);
            }
            if (total > limites.entradasMaximas()) {
                throw new ImportacaoRejeitadaException(Motivo.ENTRADAS_DEMAIS);
            }
            Enumeration<? extends ZipEntry> todas = arquivo.entries();
            int lidas = 0;
            while (todas.hasMoreElements()) {
                ZipEntry e = todas.nextElement();
                lidas++;
                if (lidas % 1000 == 0) {
                    cancelamento.verificar();
                    verificarTempo(inicio, limites);
                    ouvinte.progresso("Inspecionando o arquivo", lidas, total);
                }
                CaminhoSeguro.Validado caminho = CaminhoSeguro.validar(e.getName(), limites);
                if (!vistos.add(caminho.chaveDeDuplicidade())) {
                    throw new ImportacaoRejeitadaException(Motivo.ENTRADA_DUPLICADA);
                }
                if (caminho.diretorio() || e.isDirectory()) {
                    continue;
                }
                long declarado = Math.max(e.getSize(), 0);
                long comprimido = Math.max(e.getCompressedSize(), 0);
                somaDeclarada += declarado;
                if (somaDeclarada > limites.tamanhoDeclaradoTotalMaximo() || somaDeclarada < 0) {
                    throw new ImportacaoRejeitadaException(Motivo.TAMANHO_DECLARADO_EXCESSIVO);
                }
                verificarRazao(declarado, comprimido, limites);
                CategoriaDeDados categoria = ClassificadorDeCategoria.classificar(caminho.caminho());
                entradas.add(new EntradaInventariada(caminho.caminho(), e.getName(), categoria,
                        FormatoDeArquivo.deCaminho(caminho.caminho()), declarado, comprimido));
            }
        } catch (ZipException e) {
            throw new ImportacaoRejeitadaException(Motivo.NAO_E_ZIP);
        } catch (IOException e) {
            throw new ImportacaoRejeitadaException(Motivo.NAO_E_ZIP);
        }

        cancelamento.verificar();
        if (entradas.stream().allMatch(e -> e.categoria() == CategoriaDeDados.DESCONHECIDA)) {
            throw new ImportacaoRejeitadaException(Motivo.SEM_DADOS_RECONHECIDOS);
        }
        InventarioDoZip inventario = new InventarioDoZip(tamanho, entradas, avisos);
        avisos.addAll(avisosDeCategoriasAusentes(inventario));
        long desconhecidos = entradas.stream().filter(e -> e.categoria() == CategoriaDeDados.DESCONHECIDA).count();
        if (desconhecidos > 0) {
            avisos.add(desconhecidos + " arquivo(s) não reconhecido(s) foram apenas inventariados e não serão processados.");
        }
        return new InventarioDoZip(tamanho, entradas, avisos);
    }

    private static long tamanhoValidado(Path zip, LimitesDeImportacao limites) {
        try {
            if (zip == null || !Files.isRegularFile(zip)) {
                throw new ImportacaoRejeitadaException(Motivo.ARQUIVO_INEXISTENTE);
            }
            long tamanho = Files.size(zip);
            if (tamanho == 0) {
                throw new ImportacaoRejeitadaException(Motivo.ZIP_VAZIO);
            }
            if (tamanho > limites.tamanhoMaximoDoZip()) {
                throw new ImportacaoRejeitadaException(Motivo.ZIP_GRANDE_DEMAIS);
            }
            return tamanho;
        } catch (IOException e) {
            throw new ImportacaoRejeitadaException(Motivo.ARQUIVO_INEXISTENTE);
        }
    }

    static void verificarRazao(long declarado, long comprimido, LimitesDeImportacao limites) {
        if (declarado < limites.limiarDaRazao()) {
            return;
        }
        long denominador = Math.max(comprimido, 1);
        if (declarado / denominador > limites.razaoMaximaDeCompressao()) {
            throw new ImportacaoRejeitadaException(Motivo.COMPRESSAO_SUSPEITA);
        }
    }

    static void verificarTempo(Instant inicio, LimitesDeImportacao limites) {
        if (Duration.between(inicio, Instant.now()).compareTo(limites.tempoMaximo()) > 0) {
            throw new ImportacaoRejeitadaException(Motivo.TEMPO_ESGOTADO);
        }
    }

    private static List<String> avisosDeCategoriasAusentes(InventarioDoZip inv) {
        List<String> avisos = new ArrayList<>();
        if (!inv.contem(CategoriaDeDados.SEGUIDORES)) {
            avisos.add("A lista de seguidores não foi encontrada. Comparações de seguidores ficarão indisponíveis.");
        }
        if (!inv.contem(CategoriaDeDados.SEGUINDO)) {
            avisos.add("A lista de contas seguidas não foi encontrada. Comparações de seguidores ficarão indisponíveis.");
        }
        return avisos;
    }
}
