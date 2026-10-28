package br.com.instadino.licenciamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArmazemEChavesTest {

    @TempDir
    Path pasta;

    @Test
    void identificadorDoDispositivoEGeradoUmaVezEReutilizado() {
        Path arquivo = pasta.resolve("sub/dispositivo.id");
        String a = IdDoDispositivo.obterOuCriar(arquivo);
        String b = IdDoDispositivo.obterOuCriar(arquivo);
        assertThat(a).isEqualTo(b).matches("[A-Za-z0-9_-]{8,64}");
    }

    @Test
    void identificadorAdulteradoEhSubstituidoPorUmValido() throws Exception {
        Path arquivo = pasta.resolve("dispositivo.id");
        Files.writeString(arquivo, "../../etc/passwd");
        assertThat(IdDoDispositivo.obterOuCriar(arquivo)).matches("[A-Za-z0-9_-]{8,64}");
    }

    @Test
    void chavesEmbutidasDoProjetoAindaNaoTemChaveDeProducao() {
        assertThat(ChavesConfiaveis.embutidas().semChaves()).as("nenhuma chave de produção foi gerada ainda").isTrue();
    }

    @Test
    void pastaDeDesenvolvimentoAcrescentaChavesPorKid() throws Exception {
        var emissor = new LicencasDeTeste("dev1");
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(emissor.par.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(pasta.resolve("dev1.pub"), pem);
        Files.writeString(pasta.resolve("lixo.txt"), "ignorado");
        var chaves = ChavesConfiaveis.vazio().comPastaDeDesenvolvimento(pasta);
        assertThat(chaves.chave("dev1")).isPresent();
        assertThat(chaves.chave("outra")).isEmpty();
        assertThat(chaves.chave(null)).isEmpty();
        assertThat(ChavesConfiaveis.vazio().comPastaDeDesenvolvimento(null).quantidade()).isZero();
    }
}
