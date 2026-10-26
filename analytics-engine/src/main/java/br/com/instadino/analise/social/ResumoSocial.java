package br.com.instadino.analise.social;

import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import java.util.Set;

/**
 * Indicadores calculados sobre as listas normalizadas e deduplicadas da exportação.
 *
 * @param mutuos contas que aparecem nas duas listas
 * @param naoSeguemDeVolta contas seguidas que não aparecem na lista de seguidores
 * @param voceNaoSegue contas que aparecem como seguidores mas não na lista de seguidos
 */
public record ResumoSocial(int totalSeguidores, int totalSeguindo, Set<NomeDeUsuario> mutuos,
                           Set<NomeDeUsuario> naoSeguemDeVolta, Set<NomeDeUsuario> voceNaoSegue) {

    /** Seguidores por conta seguida; nulo quando não há contas seguidas (divisão indefinida). */
    public Double proporcaoSeguidoresPorSeguindo() {
        return totalSeguindo == 0 ? null : (double) totalSeguidores / totalSeguindo;
    }
}
