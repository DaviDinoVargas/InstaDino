package br.com.instadino.desktop.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiretorioDeDadosTest {

    @Test
    void variavelDeAmbienteTemPrioridade() {
        var env = Map.of("INSTADINO_DADOS", "/dados/meus", "LOCALAPPDATA", "C:/Users/a/AppData/Local");
        assertThat(DiretorioDeDados.calcular(env::get, "Windows 11", "/home/a")).isEqualTo(Path.of("/dados/meus"));
    }

    @Test
    void noWindowsOsDadosFicamForaDaPastaDeInstalacao() {
        var env = Map.of("LOCALAPPDATA", "C:/Users/a/AppData/Local");
        Path dados = DiretorioDeDados.calcular(env::get, "Windows 11", "C:/Users/a");
        Path instalacao = Path.of("C:/Users/a/AppData/Local", "InstaDino"); // destino do instalador por usuário (jpackage)
        assertThat(dados).isEqualTo(Path.of("C:/Users/a/AppData/Local", "InstaDinoDados"));
        assertThat(dados).isNotEqualTo(instalacao);
        assertThat(dados.startsWith(instalacao)).isFalse();
    }

    @Test
    void foraDoWindowsSegueOPadraoXdg() {
        assertThat(DiretorioDeDados.calcular(k -> null, "Linux", "/home/a")).isEqualTo(Path.of("/home/a/.local/share/instadino"));
        assertThat(DiretorioDeDados.calcular(Map.of("XDG_DATA_HOME", "/xdg")::get, "Linux", "/home/a")).isEqualTo(Path.of("/xdg/instadino"));
    }

    @Test
    void valorEmBrancoNaoSubstituiOPadrao() {
        assertThat(DiretorioDeDados.calcular(Map.of("INSTADINO_DADOS", "  ")::get, "Linux", "/home/a")).isEqualTo(Path.of("/home/a/.local/share/instadino"));
    }
}
