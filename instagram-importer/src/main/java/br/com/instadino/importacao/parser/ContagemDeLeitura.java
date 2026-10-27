package br.com.instadino.importacao.parser;

import java.util.Map;
import java.util.TreeMap;

/** Contadores de uma leitura genérica: elementos, descartes por motivo, formas observadas e período das datas. */
public final class ContagemDeLeitura {

    private final Map<String, Integer> rejeitadosPorMotivo = new TreeMap<>();
    private final Map<String, Integer> formas = new TreeMap<>();
    private int elementos;
    private int aceitos;
    private Long primeiroInstante;
    private Long ultimoInstante;

    void elemento(String forma) {
        elementos++;
        if (formas.containsKey(forma) || formas.size() < 12) {
            formas.merge(forma, 1, Integer::sum);
        } else {
            formas.merge("(outras formas)", 1, Integer::sum);
        }
    }

    void aceitar(Long instante) {
        aceitos++;
        if (instante != null) {
            primeiroInstante = primeiroInstante == null ? instante : Math.min(primeiroInstante, instante);
            ultimoInstante = ultimoInstante == null ? instante : Math.max(ultimoInstante, instante);
        }
    }

    void rejeitar(String motivo) {
        rejeitadosPorMotivo.merge(motivo, 1, Integer::sum);
    }

    public int elementos() {
        return elementos;
    }

    public int aceitos() {
        return aceitos;
    }

    public Map<String, Integer> rejeitadosPorMotivo() {
        return rejeitadosPorMotivo;
    }

    public Map<String, Integer> formas() {
        return formas;
    }

    public Long primeiroInstante() {
        return primeiroInstante;
    }

    public Long ultimoInstante() {
        return ultimoInstante;
    }
}
