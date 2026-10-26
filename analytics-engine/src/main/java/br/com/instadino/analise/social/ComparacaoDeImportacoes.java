package br.com.instadino.analise.social;

import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import java.util.Collection;
import java.util.Set;

/**
 * Diferenças OBSERVADAS entre duas exportações da mesma lista. A ausência de uma conta na exportação mais recente
 * não é, por si só, a confirmação de um evento (como deixar de seguir) em uma data específica.
 *
 * @param novas presentes na exportação mais recente e ausentes na anterior
 * @param ausentes presentes na exportação anterior e ausentes na mais recente
 * @param mantidas presentes nas duas
 * @param confiavel falso quando alguma das listas está incompleta ou veio de importação parcial
 */
public record ComparacaoDeImportacoes(Set<NomeDeUsuario> novas, Set<NomeDeUsuario> ausentes, Set<NomeDeUsuario> mantidas,
                                      boolean confiavel) {

    public static final String LIMITACAO =
            "Diferenças são observações entre duas exportações, não eventos confirmados. Uma conta ausente na exportação "
                    + "mais recente pode ter deixado de seguir, ter sido desativada, ter mudado de nome ou a lista pode estar incompleta. "
                    + "Também não é possível saber em que data ocorreu a mudança, apenas que ocorreu entre as duas exportações.";

    public static ComparacaoDeImportacoes comparar(Collection<NomeDeUsuario> anterior, Collection<NomeDeUsuario> maisRecente,
                                                   boolean anteriorCompleta, boolean maisRecenteCompleta) {
        DiferencaDeConjuntos<NomeDeUsuario> d = DiferencaDeConjuntos.entre(anterior, maisRecente);
        return new ComparacaoDeImportacoes(d.somenteNoSegundo(), d.somenteNoPrimeiro(), d.emAmbos(),
                anteriorCompleta && maisRecenteCompleta);
    }
}
