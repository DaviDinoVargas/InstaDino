package br.com.instadino.importacao;

import java.util.List;

/**
 * Resumo agregado de uma conversa da exportação: contagens e datas, <strong>nunca o texto</strong> das mensagens.
 *
 * @param titulo título da conversa (nome do contato ou do grupo), com a codificação corrigida
 * @param participantes quantidade de participantes, incluindo você
 * @param total mensagens da conversa
 * @param enviadas mensagens enviadas por você, ou {@code -1} quando não foi possível identificar qual participante é o titular
 * @param midias mensagens que contêm foto, vídeo, áudio, GIF ou figurinha
 * @param meses mensagens por mês (UTC), em ordem cronológica
 */
public record ResumoDeConversa(String titulo, int participantes, int total, int enviadas, int midias, Long primeiroInstante,
                               Long ultimoInstante, List<Mes> meses) {

    public record Mes(String mes, int total, int enviadas) {
    }

    public ResumoDeConversa {
        meses = List.copyOf(meses);
    }

    public boolean grupo() {
        return participantes > 2;
    }

    public boolean titularIdentificado() {
        return enviadas >= 0;
    }
}
