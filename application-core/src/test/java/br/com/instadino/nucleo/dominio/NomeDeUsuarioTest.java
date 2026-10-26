package br.com.instadino.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NomeDeUsuarioTest {

    @ParameterizedTest
    @CsvSource({
        "Maria.Silva, maria.silva",
        "'  @JOAO_99  ', joao_99",
        "a, a",
        "ABC_def.123, abc_def.123"
    })
    void normalizaParaMinusculasSemArrobaNemEspacos(String bruto, String esperado) {
        assertThat(NomeDeUsuario.tentar(bruto)).map(NomeDeUsuario::valor).contains(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "@", "com espaço", "acentuação", "a/b", "a\\b", "..\\x", "<script>", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
        "user;DROP", "user\nname", "😀", "usuário"})
    void rejeitaNomesInvalidos(String bruto) {
        assertThat(NomeDeUsuario.tentar(bruto)).isEmpty();
    }

    @Test
    void rejeitaNuloEConstrutorDireto() {
        assertThat(NomeDeUsuario.tentar(null)).isEmpty();
        assertThatThrownBy(() -> new NomeDeUsuario("Maiuscula")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "https://www.instagram.com/maria.silva, maria.silva",
        "https://www.instagram.com/_u/joao_99, joao_99",
        "https://instagram.com/Fulano/?hl=pt, fulano",
        "http://m.instagram.com/beltrano#x, beltrano"
    })
    void extraiUsuarioDeUrlDePerfil(String url, String esperado) {
        assertThat(NomeDeUsuario.deUrlDePerfil(url)).map(NomeDeUsuario::valor).contains(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.com/maria", "https://instagram.com.evil.com/maria", "https://www.instagram.com/",
        "https://notinstagram.com/x", "maria", "javascript:alert(1)"})
    void ignoraUrlsQueNaoSaoDePerfilDoInstagram(String url) {
        assertThat(NomeDeUsuario.deUrlDePerfil(url)).isEmpty();
    }

    @Test
    void urlDoPerfilEReconstruidaSemDadosExternos() {
        assertThat(new NomeDeUsuario("maria").urlDoPerfil()).isEqualTo("https://www.instagram.com/maria/");
    }
}
