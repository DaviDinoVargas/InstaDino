package br.com.instadino.importacao.parser;

import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Registros lidos de um arquivo de relacionamentos, já normalizados, com contagem de registros inválidos. */
public final class ResultadoDeLeitura {

    private final Map<NomeDeUsuario, Long> usuarios = new LinkedHashMap<>();
    private final Map<String, Integer> invalidosPorMotivo = new TreeMap<>();
    private int lidos;
    private int duplicados;

    void aceitar(NomeDeUsuario usuario, Long instante) {
        lidos++;
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
