package br.com.instadino.importacao;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * O que o importador fez com UM arquivo de relacionamentos: quantos elementos leu, quantos aceitou e descartou (e por
 * quê) e qual a FORMA do JSON (somente nomes de chaves, nunca valores). Serve para entender divergências entre a
 * exportação real e o que o leitor espera, sem expor nomes de usuário nem dados pessoais.
 *
 * @param caminho caminho dentro do ZIP, sem a pasta raiz da exportação (que costuma conter o nome do usuário)
 * @param erro motivo genérico quando o arquivo inteiro não pôde ser lido; nulo se a leitura funcionou
 */
public record DiagnosticoDeArquivo(String caminho, CategoriaDeDados categoria, long tamanhoEmBytes, int elementosLidos, int validos,
                                   int duplicados, Map<String, Integer> invalidosPorMotivo, Map<String, Integer> formas, String erro) {

    private static final Set<String> PASTAS_CONHECIDAS = Set.of("connections", "your_instagram_activity", "personal_information",
            "ads_information", "security_and_login_information", "logged_information", "preferences", "media", "messages",
            "followers_and_following", "content", "apps_and_websites_off_of_instagram", "your_topics", "information_about_you",
            "account_information", "following_and_followers", "contacts", "past_instagram_insights", "scheduled_content");

    public DiagnosticoDeArquivo {
        invalidosPorMotivo = Map.copyOf(new TreeMap<>(invalidosPorMotivo));
        formas = Map.copyOf(new TreeMap<>(formas));
    }

    /** Remove a pasta raiz da exportação (pode conter o nome de usuário), mantendo a estrutura interna. */
    public static String caminhoSemRaiz(String caminho) {
        String[] partes = caminho.split("/");
        for (int i = 0; i < partes.length - 1; i++) {
            if (PASTAS_CONHECIDAS.contains(partes[i].toLowerCase(Locale.ROOT))) {
                return String.join("/", List.of(partes).subList(i, partes.length));
            }
        }
        return partes.length <= 2 ? caminho : String.join("/", List.of(partes).subList(partes.length - 2, partes.length));
    }

    public int descartados() {
        return invalidosPorMotivo.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Texto seguro para copiar e colar em um relatório de problema: só estrutura e contagens. */
    public String comoTexto() {
        StringBuilder s = new StringBuilder();
        s.append(caminho).append(" [").append(categoria.name()).append(", ").append(tamanhoEmBytes).append(" bytes]\n");
        s.append("  elementos lidos: ").append(elementosLidos).append(" | aceitos (únicos): ").append(validos)
                .append(" | repetidos: ").append(duplicados).append(" | descartados: ").append(descartados()).append('\n');
        invalidosPorMotivo.forEach((m, n) -> s.append("  descartados por \"").append(m).append("\": ").append(n).append('\n'));
        formas.forEach((f, n) -> s.append("  forma ").append(n).append("x: ").append(f).append('\n'));
        if (erro != null) {
            s.append("  ERRO: ").append(erro).append('\n');
        }
        return s.toString();
    }
}
