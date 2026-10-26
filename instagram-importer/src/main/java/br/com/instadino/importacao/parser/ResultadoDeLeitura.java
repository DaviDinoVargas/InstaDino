package br.com.instadino.importacao.parser;

import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Registros lidos de um arquivo de relacionamentos, já normalizados, com contagem de registros inválidos. */
public final class ResultadoDeLeitura {

    private final Map<NomeDeUsuario, Long> usuarios = new LinkedHashMap<>();
    private final Map<String, Integer> invalidosPorMotivo = new TreeMap<>();
    private final Map<String, Integer> formas = new TreeMap<>();
    private Long primeiroInstante;
    private Long ultimoInstante;
    private int lidos;
    private int duplicados;

    void aceitar(NomeDeUsuario usuario, Long instante) {
        lidos++;
        if (instante != null) {
            primeiroInstante = primeiroInstante == null ? instante : Math.min(primeiroInstante, instante);
            ultimoInstante = ultimoInstante == null ? instante : Math.max(ultimoInstante, instante);
        }
        Long existente = usuarios.get(usuario);
        if (usuarios.containsKey(usuario)) {
            duplicados++;
            if (instante != null && (existente == null || instante < existente)) {
                usuarios.put(usuario, instante);
            }
        } else {
            usuarios.put(usuario, instante);
        }
    }

    /** Registra a forma (apenas nomes de chaves) de um elemento; guarda no máximo 12 formas distintas. */
    void registrarForma(String forma) {
        if (formas.containsKey(forma) || formas.size() < 12) {
            formas.merge(forma, 1, Integer::sum);
        } else {
            formas.merge("(outras formas)", 1, Integer::sum);
        }
    }

    void rejeitar(String motivo) {
        lidos++;
        invalidosPorMotivo.merge(motivo, 1, Integer::sum);
    }

    /** Usuários únicos, com o menor carimbo de tempo encontrado (ou nulo). */
    public Map<NomeDeUsuario, Long> usuarios() {
        return usuarios;
    }

    public Map<String, Integer> invalidosPorMotivo() {
        return invalidosPorMotivo;
    }

    /** Menor carimbo de tempo (segundos) visto nos registros aceitos, ou nulo. */
    public Long primeiroInstante() {
        return primeiroInstante;
    }

    public Long ultimoInstante() {
        return ultimoInstante;
    }

    public Map<String, Integer> formas() {
        return formas;
    }

    public int registrosLidos() {
        return lidos;
    }

    public int duplicados() {
        return duplicados;
    }

    public int invalidos() {
        return invalidosPorMotivo.values().stream().mapToInt(Integer::intValue).sum();
    }
}
