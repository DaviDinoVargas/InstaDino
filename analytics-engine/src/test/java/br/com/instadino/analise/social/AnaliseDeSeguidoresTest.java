package br.com.instadino.analise.social;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AnaliseDeSeguidoresTest {

    private static List<NomeDeUsuario> u(String... nomes) {
        return java.util.Arrays.stream(nomes).map(n -> NomeDeUsuario.tentar(n).orElseThrow()).toList();
    }

    private static Set<String> v(Set<NomeDeUsuario> s) {
        return s.stream().map(NomeDeUsuario::valor).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    @Test
    void calculaIndicadoresBasicos() {
        ResumoSocial r = AnaliseDeSeguidores.analisar(u("ana", "bruno", "carla", "diego"), u("bruno", "carla", "eduardo", "fernanda"));
        assertThat(r.totalSeguidores()).isEqualTo(4);
        assertThat(r.totalSeguindo()).isEqualTo(4);
        assertThat(v(r.mutuos())).containsExactly("bruno", "carla");
        assertThat(v(r.naoSeguemDeVolta())).containsExactly("eduardo", "fernanda");
        assertThat(v(r.voceNaoSegue())).containsExactly("ana", "diego");
        assertThat(r.proporcaoSeguidoresPorSeguindo()).isEqualTo(1.0);
    }

    @Test
    void repeticoesContamUmaVez() {
        ResumoSocial r = AnaliseDeSeguidores.analisar(u("ana", "ana", "bruno"), u("ana", "ana", "ana"));
        assertThat(r.totalSeguidores()).isEqualTo(2);
        assertThat(r.totalSeguindo()).isEqualTo(1);
        assertThat(v(r.mutuos())).containsExactly("ana");
    }

    @Test
    void listasVaziasNaoCausamDivisaoPorZero() {
        ResumoSocial r = AnaliseDeSeguidores.analisar(u("ana"), u());
        assertThat(r.proporcaoSeguidoresPorSeguindo()).isNull();
        assertThat(r.totalSeguindo()).isZero();
        ResumoSocial vazio = AnaliseDeSeguidores.analisar(u(), u());
        assertThat(vazio.mutuos()).isEmpty();
    }

    @Test
    void conjuntosSaoConsistentesEntreSi() {
        var seguidores = u("a", "b", "c", "d", "e");
        var seguindo = u("c", "d", "e", "f", "g", "h");
        ResumoSocial r = AnaliseDeSeguidores.analisar(seguidores, seguindo);
        assertThat(r.mutuos().size() + r.voceNaoSegue().size()).isEqualTo(r.totalSeguidores());
        assertThat(r.mutuos().size() + r.naoSeguemDeVolta().size()).isEqualTo(r.totalSeguindo());
    }

    @Test
    void comparacaoEntreImportacoesSeparaNovasAusentesEMantidas() {
        var c = ComparacaoDeImportacoes.comparar(u("ana", "bruno", "carla"), u("bruno", "carla", "diego"), true, true);
        assertThat(v(c.novas())).containsExactly("diego");
        assertThat(v(c.ausentes())).containsExactly("ana");
        assertThat(v(c.mantidas())).containsExactly("bruno", "carla");
        assertThat(c.confiavel()).isTrue();
    }

    @Test
    void listaIncompletaTornaAComparacaoNaoConfiavel() {
        assertThat(ComparacaoDeImportacoes.comparar(u("a"), u("a"), true, false).confiavel()).isFalse();
        assertThat(ComparacaoDeImportacoes.comparar(u("a"), u("a"), false, true).confiavel()).isFalse();
    }

    @Test
    void limitacoesDeixamClaroQueDiferencaNaoEEventoConfirmado() {
        assertThat(ComparacaoDeImportacoes.LIMITACAO).contains("não eventos confirmados");
        assertThat(AnaliseDeSeguidores.LIMITACAO).contains("não prova");
    }

    @Test
    void comparacaoManualDeListasArbitrarias() {
        var d = DiferencaDeConjuntos.entre(List.of("x", "y"), List.of("y", "z"));
        assertThat(d.somenteNoPrimeiro()).containsExactly("x");
        assertThat(d.somenteNoSegundo()).containsExactly("z");
        assertThat(d.emAmbos()).containsExactly("y");
    }

    @Test
    void escalaParaMuitosRegistros() {
        var a = new java.util.ArrayList<NomeDeUsuario>();
        var b = new java.util.ArrayList<NomeDeUsuario>();
        for (int i = 0; i < 200_000; i++) {
            a.add(new NomeDeUsuario("u" + i));
            if (i % 2 == 0) {
                b.add(new NomeDeUsuario("u" + i));
            }
        }
        ResumoSocial r = AnaliseDeSeguidores.analisar(a, b);
        assertThat(r.mutuos()).hasSize(100_000);
        assertThat(r.voceNaoSegue()).hasSize(100_000);
    }
}
