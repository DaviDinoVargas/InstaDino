package br.com.instadino.importacao;

import br.com.instadino.importacao.parser.LeitorDeMensagens;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Junta os arquivos {@code message_N.json} de cada conversa e, no fim, descobre qual participante é o titular da conta:
 * o único nome que aparece em mais conversas do que qualquer outro. Com uma única conversa não há como saber, e as
 * contagens de "enviadas" ficam indisponíveis em vez de chutadas.
 */
final class AcumuladorDeConversas {

    static final int MAXIMO_DE_CONVERSAS = 20_000;

    private static final class Conversa {
        String titulo;
        final java.util.Set<String> participantes = new java.util.LinkedHashSet<>();
        final Map<String, Map<String, Integer>> porRemetente = new LinkedHashMap<>();
        int midias;
        Long primeiro;
        Long ultimo;
    }

    private final Map<String, Conversa> conversas = new LinkedHashMap<>();
    private boolean limiteAtingido;

    /** Devolve falso se a conversa foi ignorada por excesso. */
    boolean adicionar(String pasta, LeitorDeMensagens.Parte parte) {
        Conversa existente = conversas.get(pasta);
        if (existente == null) {
            if (conversas.size() >= MAXIMO_DE_CONVERSAS) {
                limiteAtingido = true;
                return false;
            }
            existente = new Conversa();
            conversas.put(pasta, existente);
        }
        Conversa c = existente;
        if (c.titulo == null) {
            c.titulo = parte.titulo();
        }
        c.participantes.addAll(parte.participantes());
        for (Map.Entry<String, Map<String, Integer>> e : parte.porRemetente().entrySet()) {
            Map<String, Integer> destino = c.porRemetente.computeIfAbsent(e.getKey(), k -> new TreeMap<>());
            e.getValue().forEach((mes, n) -> destino.merge(mes, n, Integer::sum));
            c.participantes.add(e.getKey());
        }
        c.midias += parte.midias();
        c.primeiro = menor(c.primeiro, parte.primeiroInstante());
        c.ultimo = maior(c.ultimo, parte.ultimoInstante());
        return true;
    }

    boolean limiteAtingido() {
        return limiteAtingido;
    }

    boolean vazio() {
        return conversas.isEmpty();
    }

    /** Resultado final. {@code titularIdentificado} informa se as contagens de enviadas são confiáveis. */
    List<ResumoDeConversa> finalizar() {
        Optional<String> titular = titular();
        List<ResumoDeConversa> resumos = new ArrayList<>();
        for (Conversa c : conversas.values()) {
            Map<String, int[]> meses = new TreeMap<>();
            int total = 0;
            int enviadas = 0;
            for (Map.Entry<String, Map<String, Integer>> e : c.porRemetente.entrySet()) {
                boolean meu = titular.isPresent() && titular.get().equals(e.getKey());
                for (Map.Entry<String, Integer> m : e.getValue().entrySet()) {
                    int[] v = meses.computeIfAbsent(m.getKey(), k -> new int[2]);
                    v[0] += m.getValue();
                    total += m.getValue();
                    if (meu) {
                        v[1] += m.getValue();
                        enviadas += m.getValue();
                    }
                }
            }
            if (total == 0) {
                continue;
            }
            List<ResumoDeConversa.Mes> lista = meses.entrySet().stream()
                    .map(e -> new ResumoDeConversa.Mes(e.getKey(), e.getValue()[0], titular.isPresent() ? e.getValue()[1] : -1)).toList();
            String titulo = c.titulo == null || c.titulo.isBlank() ? "(sem título)" : c.titulo;
            resumos.add(new ResumoDeConversa(titulo, Math.max(c.participantes.size(), 1), total, titular.isPresent() ? enviadas : -1,
                    c.midias, c.primeiro, c.ultimo, lista));
        }
        resumos.sort((a, b) -> Integer.compare(b.total(), a.total()));
        return resumos;
    }

    private Optional<String> titular() {
        if (conversas.size() < 2) {
            return Optional.empty();
        }
        Map<String, Integer> presenca = new HashMap<>();
        for (Conversa c : conversas.values()) {
            for (String nome : c.participantes) {
                presenca.merge(nome, 1, Integer::sum);
            }
        }
        presenca.remove(LeitorDeMensagens.OUTROS);
        String melhor = null;
        int maior = 0;
        int segundo = 0;
        for (Map.Entry<String, Integer> e : presenca.entrySet()) {
            if (e.getValue() > maior) {
                segundo = maior;
                maior = e.getValue();
                melhor = e.getKey();
            } else if (e.getValue() > segundo) {
                segundo = e.getValue();
            }
        }
        return melhor != null && maior > segundo ? Optional.of(melhor) : Optional.empty();
    }

    private static Long menor(Long a, Long b) {
        return a == null ? b : b == null ? a : Math.min(a, b);
    }

    private static Long maior(Long a, Long b) {
        return a == null ? b : b == null ? a : Math.max(a, b);
    }
}
