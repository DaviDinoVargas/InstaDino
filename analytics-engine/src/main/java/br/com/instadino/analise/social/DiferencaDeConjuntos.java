package br.com.instadino.analise.social;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Diferença entre dois conjuntos já normalizados e deduplicados.
 *
 * @param somenteNoPrimeiro elementos que estão no primeiro conjunto e não no segundo
 * @param somenteNoSegundo elementos que estão no segundo conjunto e não no primeiro
 * @param emAmbos interseção
 */
public record DiferencaDeConjuntos<T extends Comparable<T>>(Set<T> somenteNoPrimeiro, Set<T> somenteNoSegundo, Set<T> emAmbos) {

    public DiferencaDeConjuntos {
        somenteNoPrimeiro = java.util.Collections.unmodifiableSet(new TreeSet<>(somenteNoPrimeiro));
        somenteNoSegundo = java.util.Collections.unmodifiableSet(new TreeSet<>(somenteNoSegundo));
        emAmbos = java.util.Collections.unmodifiableSet(new TreeSet<>(emAmbos));
    }

    public static <T extends Comparable<T>> DiferencaDeConjuntos<T> entre(Collection<T> primeiro, Collection<T> segundo) {
        Set<T> a = new TreeSet<>(primeiro);
        Set<T> b = new TreeSet<>(segundo);
        Set<T> ambos = new TreeSet<>(a);
        ambos.retainAll(b);
        Set<T> soA = new TreeSet<>(a);
        soA.removeAll(b);
        Set<T> soB = new TreeSet<>(b);
        soB.removeAll(a);
        return new DiferencaDeConjuntos<>(soA, soB, ambos);
    }
}
