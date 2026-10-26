package br.com.instadino.analise.social;

import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import java.util.Collection;

/**
 * Calcula os indicadores sociais básicos. Regras de tratamento:
 * <ul>
 *   <li>as listas são convertidas em conjuntos: repetições contam uma vez;</li>
 *   <li>a comparação usa o nome de usuário normalizado (minúsculas);</li>
 *   <li>"não segue de volta" significa apenas que a conta não aparece na lista de seguidores exportada.</li>
 * </ul>
 */
public final class AnaliseDeSeguidores {

    /** Texto obrigatório nas telas e relatórios que exibem "não segue de volta". */
    public static final String LIMITACAO =
            "Esta análise compara as listas da exportação. Uma conta ausente da lista de seguidores não prova que "
                    + "deixou de seguir você: a lista pode estar incompleta, a conta pode ter sido desativada ou "
                    + "alterado o nome de usuário.";

    private AnaliseDeSeguidores() {
    }

    public static ResumoSocial analisar(Collection<NomeDeUsuario> seguidores, Collection<NomeDeUsuario> seguindo) {
        DiferencaDeConjuntos<NomeDeUsuario> d = DiferencaDeConjuntos.entre(seguidores, seguindo);
        return new ResumoSocial(
                d.somenteNoPrimeiro().size() + d.emAmbos().size(),
                d.somenteNoSegundo().size() + d.emAmbos().size(),
                d.emAmbos(),
                d.somenteNoSegundo(),
                d.somenteNoPrimeiro());
    }
}
