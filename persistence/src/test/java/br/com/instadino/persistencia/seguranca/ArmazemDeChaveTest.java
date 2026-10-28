package br.com.instadino.persistencia.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArmazemDeChaveTest {

    @TempDir
    Path pasta;

    /** Protetor de teste que realmente transforma os bytes, para provar que o arquivo não guarda a chave em claro. */
    private static final class Mascara implements ProtetorDeSegredo {
        @Override
        public byte[] proteger(byte[] s) {
            byte[] r = new byte[s.length + 4];
            for (int i = 0; i < s.length; i++) {
                r[i + 4] = (byte) (s[i] ^ 0x5A);
            }
            return r;
        }

        @Override
        public byte[] revelar(byte[] p) {
            byte[] r = new byte[p.length - 4];
            for (int i = 0; i < r.length; i++) {
                r[i] = (byte) (p[i + 4] ^ 0x5A);
            }
            return r;
        }

        @Override
        public boolean seguroParaProducao() {
            return false;
        }

        @Override
        public String nome() {
            return "máscara de teste";
        }
    }

    @Test
    void geraChaveAleatoriaDe256BitsEPersisteProtegida() throws Exception {
        var armazem = new ArmazemDeChave(pasta.resolve("chave.bin"), new Mascara());
        assertThat(armazem.existe()).isFalse();
        byte[] chave = armazem.obterOuCriar();
        assertThat(chave).hasSize(32).isNotEqualTo(new byte[32]);
        assertThat(armazem.existe()).isTrue();
        byte[] noDisco = Files.readAllBytes(pasta.resolve("chave.bin"));
        assertThat(Arrays.equals(noDisco, chave)).isFalse();
        assertThat(indexOf(noDisco, chave)).as("a chave em claro não pode aparecer no arquivo").isEqualTo(-1);
    }

    @Test
    void recuperaAMesmaChaveEmNovaInstancia() {
        byte[] primeira = new ArmazemDeChave(pasta.resolve("chave.bin"), new Mascara()).obterOuCriar();
        byte[] segunda = new ArmazemDeChave(pasta.resolve("chave.bin"), new Mascara()).obterOuCriar();
        assertThat(segunda).isEqualTo(primeira);
    }

    @Test
    void instalacoesDiferentesTemChavesDiferentes() {
        byte[] a = new ArmazemDeChave(pasta.resolve("a.bin"), new Mascara()).obterOuCriar();
        byte[] b = new ArmazemDeChave(pasta.resolve("b.bin"), new Mascara()).obterOuCriar();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void arquivoCorrompidoEDetectado() throws Exception {
        Files.write(pasta.resolve("chave.bin"), new byte[] {1, 2, 3, 4, 5, 6});
        var armazem = new ArmazemDeChave(pasta.resolve("chave.bin"), new Mascara());
        assertThatThrownBy(armazem::obterOuCriar).isInstanceOf(ProtecaoIndisponivelException.class);
    }

    @Test
    void foraDoWindowsSemOptInOBancoNaoECriado() {
        String anterior = System.getProperty(ProtetoresPadrao.PROPRIEDADE_MODO_INSEGURO);
        System.clearProperty(ProtetoresPadrao.PROPRIEDADE_MODO_INSEGURO);
        try {
            if (!ProtetorDpapi.disponivel()) {
                assertThatThrownBy(ProtetoresPadrao::escolher).isInstanceOf(ProtecaoIndisponivelException.class);
                System.setProperty(ProtetoresPadrao.PROPRIEDADE_MODO_INSEGURO, "true");
                ProtetorDeSegredo p = ProtetoresPadrao.escolher();
                assertThat(p.seguroParaProducao()).isFalse();
            }
        } finally {
            if (anterior != null) {
                System.setProperty(ProtetoresPadrao.PROPRIEDADE_MODO_INSEGURO, anterior);
            } else {
                System.clearProperty(ProtetoresPadrao.PROPRIEDADE_MODO_INSEGURO);
            }
        }
    }

    @Test
    void arquivoDeChaveTemPermissaoRestritaEmSistemasPosix() throws Exception {
        new ArmazemDeChave(pasta.resolve("chave.bin"), new Mascara()).obterOuCriar();
        try {
            var perms = Files.getPosixFilePermissions(pasta.resolve("chave.bin"));
            assertThat(perms).containsExactlyInAnyOrder(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
        } catch (UnsupportedOperationException e) {
            // Windows: coberto pela DPAPI
        }
    }

    private static int indexOf(byte[] palheiro, byte[] agulha) {
        outer:
        for (int i = 0; i <= palheiro.length - agulha.length; i++) {
            for (int j = 0; j < agulha.length; j++) {
                if (palheiro[i + j] != agulha[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
