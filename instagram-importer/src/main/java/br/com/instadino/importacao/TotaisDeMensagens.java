package br.com.instadino.importacao;

import br.com.instadino.importacao.parser.ContagemDeLeitura;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Soma a leitura de todos os arquivos de mensagens em um único diagnóstico. Os nomes das pastas das conversas contêm
 * nomes de usuário e por isso nunca aparecem no diagnóstico, que o usuário pode copiar e compartilhar.
 */
final class TotaisDeMensagens {

    private int arquivos;
    private long bytes;
    private int elementos;
    private int aceitos;
    private final Map<String, Integer> rejeitados = new TreeMap<>();
    private Long primeiro;
    private Long ultimo;

    void somar(long bytesLidos, ContagemDeLeitura c) {
        arquivos++;
        bytes += bytesLidos;
        elementos += c.elementos();
        aceitos += c.aceitos();
        c.rejeitadosPorMotivo().forEach((motivo, n) -> rejeitados.merge(motivo, n, Integer::sum));
        if (c.primeiroInstante() != null) {
            primeiro = primeiro == null ? c.primeiroInstante() : Math.min(primeiro, c.primeiroInstante());
        }
        if (c.ultimoInstante() != null) {
            ultimo = ultimo == null ? c.ultimoInstante() : Math.max(ultimo, c.ultimoInstante());
        }
    }

    Optional<DiagnosticoDeArquivo> diagnostico() {
        if (arquivos == 0) {
            return Optional.empty();
        }
        return Optional.of(new DiagnosticoDeArquivo("messages/ (" + arquivos + " arquivos de conversas)", CategoriaDeDados.MENSAGENS, bytes,
                elementos, aceitos, 0, rejeitados, Map.of("mensagem", elementos), primeiro, ultimo, null));
    }
}
