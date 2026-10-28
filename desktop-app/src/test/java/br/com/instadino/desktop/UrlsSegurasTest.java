package br.com.instadino.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlsSegurasTest {

    @Test
    void aceitaApenasOFormatoExatoGeradoPeloAplicativo() {
        assertThat(UrlsSeguras.perfilDoInstagram("https://www.instagram.com/maria.silva/")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://www.instagram.com/maria/", "https://evil.com/maria/", "https://www.instagram.com.evil.com/maria/",
        "https://www.instagram.com/maria", "https://www.instagram.com/Maria/", "javascript:alert(1)", "file:///etc/passwd",
        "https://www.instagram.com/maria/?x=1", "https://www.instagram.com/a b/", "", "https://www.instagram.com//"})
    void rejeitaQualquerOutroEndereco(String url) {
        assertThat(UrlsSeguras.perfilDoInstagram(url)).isFalse();
    }

    @Test
    void rejeitaNulo() {
        assertThat(UrlsSeguras.perfilDoInstagram(null)).isFalse();
    }
}
