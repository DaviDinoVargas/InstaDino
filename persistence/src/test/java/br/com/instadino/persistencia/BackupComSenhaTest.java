package br.com.instadino.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupComSenhaTest {

    private static final String SENHA = "uma senha longa e única 2026";

    @TempDir
    Path pasta;

    private BancoLocal abrir(Path arquivo, byte[] chave) {
        return BancoLocal.abrir(arquivo, chave, CarregadorDeMigracoes.doClasspath());
    }

    private void gravar(BancoLocal banco, String chave, String valor) {
        banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO preferencia (chave, valor) VALUES (?, ?)")) {
                p.setString(1, chave);
                p.setString(2, valor);
                p.executeUpdate();
            }
            return null;
        });
    }

    private String ler(BancoLocal banco, String chave) {
        return banco.emTransacao(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT valor FROM preferencia WHERE chave = ?")) {
                p.setString(1, chave);
                try (ResultSet r = p.executeQuery()) {
                    return r.next() ? r.getString(1) : null;
                }
            }
        });
    }

    private Path criarBackup(String valor) {
        Path backup = pasta.resolve("origem.instadinobkp");
        try (BancoLocal banco = abrir(pasta.resolve("origem.db"), Chaves.A)) {
            gravar(banco, "marca", valor);
            BackupComSenha.criar(banco, backup, SENHA.toCharArray());
        }
        return backup;
    }

    @Test
    void backupNaoContemTextoEmClaroNemOCabecalhoDoSqlite() throws Exception {
        Path backup = criarBackup("VALOR_SECRETO_DO_BACKUP_5151");
        String texto = new String(Files.readAllBytes(backup), StandardCharsets.ISO_8859_1);
        assertThat(texto).startsWith("INSTADBK").doesNotContain("SQLite format 3").doesNotContain("VALOR_SECRETO_DO_BACKUP_5151")
                .doesNotContain("CREATE TABLE").doesNotContain("perfil_local");
    }

    @Test
    void restauraEmOutraInstalacaoComOutraChaveSemPrecisarDaChaveOriginal() {
        Path backup = criarBackup("PORTATIL");
        // computador novo: outra pasta, outra chave (a chave original, protegida pela DPAPI, foi perdida)
        Path novoBanco = pasta.resolve("novo").resolve("instadino.db");
        BackupComSenha.restaurar(backup, SENHA.toCharArray(), novoBanco, Chaves.B);
        try (BancoLocal banco = abrir(novoBanco, Chaves.B)) {
            assertThat(ler(banco, "marca")).isEqualTo("PORTATIL");
            banco.verificarCifragemAtiva();
        }
        assertThatThrownBy(() -> abrir(novoBanco, Chaves.A)).isInstanceOf(BancoException.class);
    }

    @Test
    void restauraSobreOBancoExistenteEPreservaOAnteriorComoAntes() throws Exception {
        Path backup = criarBackup("DO_BACKUP");
        Path destino = pasta.resolve("atual.db");
        try (BancoLocal atual = abrir(destino, Chaves.B)) {
            gravar(atual, "marca", "ATUAL");
        }
        BackupComSenha.restaurar(backup, SENHA.toCharArray(), destino, Chaves.B);
        try (BancoLocal banco = abrir(destino, Chaves.B)) {
            assertThat(ler(banco, "marca")).isEqualTo("DO_BACKUP");
        }
        assertThat(destino.resolveSibling("atual.db.antes")).exists();
    }

    @Test
    void senhaErradaNaoAlteraNadaEDaMensagemUniforme() throws Exception {
        Path backup = criarBackup("X");
        Path destino = pasta.resolve("atual.db");
        try (BancoLocal atual = abrir(destino, Chaves.B)) {
            gravar(atual, "marca", "ATUAL");
        }
        byte[] antes = Files.readAllBytes(destino);
        assertThatThrownBy(() -> BackupComSenha.restaurar(backup, "outra senha totalmente errada".toCharArray(), destino, Chaves.B))
                .isInstanceOf(BancoException.class).hasMessage("Senha incorreta ou arquivo de backup inválido.");
        assertThat(Files.readAllBytes(destino)).isEqualTo(antes);
        try (Stream<Path> arquivos = Files.list(pasta)) {
            assertThat(arquivos.map(p -> p.getFileName().toString())).noneMatch(n -> n.startsWith("restauracao-preparada-"));
        }
    }

    @Test
    void arquivoAdulteradoTruncadoOuDeOutroTipoEhRecusadoComAMesmaMensagem() throws Exception {
        Path backup = criarBackup("X");
        byte[] bytes = Files.readAllBytes(backup);
        Path adulterado = pasta.resolve("adulterado.bkp");
        byte[] copia = bytes.clone();
        copia[copia.length / 2] ^= 0x55;
        Files.write(adulterado, copia);
        Path truncado = pasta.resolve("truncado.bkp");
        Files.write(truncado, Arrays.copyOf(bytes, bytes.length / 2));
        Path semMagica = pasta.resolve("estranho.bkp");
        Files.write(semMagica, "isto não é um backup do InstaDino, apenas texto qualquer".getBytes(StandardCharsets.UTF_8));
        Path vazio = Files.write(pasta.resolve("vazio.bkp"), new byte[0]);
        for (Path ruim : new Path[] {adulterado, truncado, semMagica, vazio, pasta.resolve("nao-existe.bkp")}) {
            Path destino = pasta.resolve("destino-" + ruim.getFileName() + ".db");
            assertThatThrownBy(() -> BackupComSenha.restaurar(ruim, SENHA.toCharArray(), destino, Chaves.B))
                    .as(ruim.getFileName().toString()).isInstanceOf(BancoException.class);
            assertThat(destino).doesNotExist();
        }
    }

    @Test
    void cabecalhoHostilComIteracoesAbsurdasOuMinusculasEhRecusadoSemDerivar() throws Exception {
        Path backup = criarBackup("X");
        for (int iteracoes : new int[] {1, 99_999, 5_000_001, Integer.MAX_VALUE, -1}) {
            byte[] bytes = Files.readAllBytes(backup);
            ByteBuffer.wrap(bytes, 9, 4).putInt(iteracoes);
            Path hostil = Files.write(pasta.resolve("hostil-" + iteracoes + ".bkp"), bytes);
            long inicio = System.nanoTime();
            assertThatThrownBy(() -> BackupComSenha.restaurar(hostil, SENHA.toCharArray(), pasta.resolve("d.db"), Chaves.B))
                    .isInstanceOf(BancoException.class);
            assertThat(System.nanoTime() - inicio).as("recusa imediata para " + iteracoes).isLessThan(2_000_000_000L);
        }
    }

    @Test
    void cadaBackupUsaUmSalDiferente() throws Exception {
        try (BancoLocal banco = abrir(pasta.resolve("origem.db"), Chaves.A)) {
            BackupComSenha.criar(banco, pasta.resolve("um.bkp"), SENHA.toCharArray());
            BackupComSenha.criar(banco, pasta.resolve("dois.bkp"), SENHA.toCharArray());
        }
        byte[] um = Files.readAllBytes(pasta.resolve("um.bkp"));
        byte[] dois = Files.readAllBytes(pasta.resolve("dois.bkp"));
        assertThat(Arrays.copyOfRange(um, 13, 29)).isNotEqualTo(Arrays.copyOfRange(dois, 13, 29));
    }

    @Test
    void senhaFracaOuCurtaEhRecusadaEAEntradaEhZerada() throws Exception {
        try (BancoLocal banco = abrir(pasta.resolve("origem.db"), Chaves.A)) {
            char[] curta = "curta123".toCharArray();
            assertThatThrownBy(() -> BackupComSenha.criar(banco, pasta.resolve("x.bkp"), curta)).isInstanceOf(IllegalArgumentException.class);
            assertThat(curta).containsOnly('\0');
            assertThat(pasta.resolve("x.bkp")).doesNotExist();
        }
    }

    @Test
    void naoSobrescreveBackupExistenteESenhaEhZeradaDepoisDoUso() throws Exception {
        Path backup = criarBackup("X");
        byte[] antes = Files.readAllBytes(backup);
        try (BancoLocal banco = abrir(pasta.resolve("origem.db"), Chaves.A)) {
            char[] senha = SENHA.toCharArray();
            assertThatThrownBy(() -> BackupComSenha.criar(banco, backup, senha)).isInstanceOf(BancoException.class);
            assertThat(senha).containsOnly('\0');
        }
        assertThat(Files.readAllBytes(backup)).isEqualTo(antes);
    }

    @Test
    void senhaEquivalenteEmUnicodeNormalizadoAbreOBackup() throws Exception {
        String composta = "contraseña única ñ 2026";            // ñ pré-composto
        String decomposta = java.text.Normalizer.normalize(composta, java.text.Normalizer.Form.NFD);
        assertThat(decomposta).isNotEqualTo(composta);
        Path backup = pasta.resolve("unicode.bkp");
        try (BancoLocal banco = abrir(pasta.resolve("origem.db"), Chaves.A)) {
            gravar(banco, "k", "v");
            BackupComSenha.criar(banco, backup, composta.toCharArray());
        }
        Path destino = pasta.resolve("destino.db");
        BackupComSenha.restaurar(backup, decomposta.toCharArray(), destino, Chaves.B);
        try (BancoLocal banco = abrir(destino, Chaves.B)) {
            assertThat(ler(banco, "k")).isEqualTo("v");
        }
    }

    @Test
    void naoDeixaTemporariosEmClaroDepoisDeCriar() throws Exception {
        criarBackup("X");
        try (Stream<Path> arquivos = Files.list(pasta)) {
            assertThat(arquivos.map(p -> p.getFileName().toString())).noneMatch(n -> n.endsWith(".parcial") || n.startsWith("backup-"));
        }
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> arquivos = Files.list(tmp)) {
            assertThat(arquivos.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("backup-protegido-"))).isEmpty();
        }
    }
}
