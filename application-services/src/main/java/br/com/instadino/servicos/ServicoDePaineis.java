package br.com.instadino.servicos;

import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import br.com.instadino.persistencia.repositorio.RepositorioDePreferencias;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Painéis personalizados (Premium): o usuário escolhe quais blocos do painel social aparecem. Guarda só os identificadores
 * dos blocos ocultos, nas preferências locais. Sem Premium, o painel completo é sempre exibido.
 */
public final class ServicoDePaineis {

    static final String CHAVE = "painel.ocultos";

    /** Blocos do painel, na ordem de exibição. */
    public static final Map<String, String> BLOCOS = blocos();

    private final PoliticaDeAcesso politica;
    private final RepositorioDePreferencias preferencias;

    ServicoDePaineis(PoliticaDeAcesso politica, RepositorioDePreferencias preferencias) {
        this.politica = politica;
        this.preferencias = preferencias;
    }

    private static Map<String, String> blocos() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seguidores", "Seguidores");
        m.put("seguindo", "Seguindo");
        m.put("mutuos", "Relações mútuas");
        m.put("nao-seguem", "Não seguem de volta");
        m.put("voce-nao-segue", "Você não segue de volta");
        m.put("proporcao", "Seguidores por conta seguida");
        m.put("composicao", "Gráfico de composição");
        m.put("importacao", "Última importação");
        m.put("atalhos", "Atalhos para as listas");
        return java.util.Collections.unmodifiableMap(m);
    }

    public boolean personalizavel() {
        return politica.permite(Funcionalidade.PAINEIS_PERSONALIZADOS);
    }

    /** Blocos ocultos. Sem o plano Premium a personalização não vale e nada fica oculto. */
    public Set<String> ocultos() {
        if (!personalizavel()) {
            return Set.of();
        }
        return preferencias.obter(CHAVE).map(v -> Arrays.stream(v.split(",")).map(String::strip).filter(BLOCOS::containsKey)
                .collect(Collectors.toCollection(TreeSet::new))).orElseGet(TreeSet::new);
    }

    public boolean visivel(String bloco) {
        return !ocultos().contains(bloco);
    }

    public void definirOcultos(Set<String> ocultos) {
        politica.exigir(Funcionalidade.PAINEIS_PERSONALIZADOS);
        preferencias.definir(CHAVE, ocultos.stream().filter(BLOCOS::containsKey).sorted().collect(Collectors.joining(",")));
    }
}
