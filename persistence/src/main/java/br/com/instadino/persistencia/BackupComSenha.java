package br.com.instadino.persistencia;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Backup portátil protegido por uma senha escolhida pelo usuário.
 *
 * <p>O backup comum ({@link BackupLocal}) herda a chave do banco, que fica protegida pela DPAPI e presa àquele usuário
 * e computador: num desastre (perda do disco, troca de máquina) a chave se perderia junto e o backup seria inútil.
 * Aqui a cópia é recifrada com uma chave derivada da senha (PBKDF2-HMAC-SHA256 da JDK, sal aleatório) e a restauração
 * recifra para a chave da instalação de destino. Nenhuma criptografia própria: a cifragem do arquivo continua sendo a
 * do SQLite3 Multiple Ciphers.
 *
 * <p>Formato: {@code "INSTADBK"} (8 bytes) · versão (1) · iterações (4, big-endian) · sal (16) · banco cifrado.
 * O cabeçalho não é segredo. A senha forte é o único controle contra tentativa offline de adivinhação.
 */
public final class BackupComSenha {

    public static final int TAMANHO_MINIMO_DA_SENHA = 12;
    static final int ITERACOES = 600_000;
    private static final int ITERACOES_MINIMAS = 100_000;
    private static final int ITERACOES_MAXIMAS = 5_000_000;
    private static final byte[] MAGICA = "INSTADBK".getBytes(StandardCharsets.US_ASCII);
    private static final byte VERSAO = 1;
    private static final int TAMANHO_DO_SAL = 16;
    private static final int TAMANHO_DO_CABECALHO = MAGICA.length + 1 + 4 + TAMANHO_DO_SAL;
    private static final String MENSAGEM_ABERTURA = "Senha incorreta ou arquivo de backup inválido.";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private BackupComSenha() {
    }

    /** Cria o backup protegido por senha em {@code destino}, que não pode existir. A senha é zerada ao final. */
    public static void criar(BancoLocal banco, Path destino, char[] senha) {
        validarSenha(senha);
        Path alvo = destino.toAbsolutePath();
        if (Files.exists(alvo)) {
            zerar(senha);
            throw new BancoException("O arquivo de backup já existe.");
        }
        byte[] chaveAtual = banco.copiaDaChave();
        byte[] sal = new byte[TAMANHO_DO_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] derivada = derivar(senha, sal, ITERACOES);
        Path pasta = null;
        Path copia = null;
        Path parcial = null;
        try {
            pasta = Files.createTempDirectory("backup-protegido-");
            copia = pasta.resolve("copia.db");
            BackupLocal.criar(banco, copia);
            try (BancoLocal c = BancoLocal.abrirParaVerificacao(copia, chaveAtual)) {
                c.trocarChave(derivada);
            }
            try (BancoLocal conferencia = BancoLocal.abrirParaVerificacao(copia, derivada)) {
                if (!conferencia.verificarIntegridade().isEmpty()) {
                    throw new BancoException("A cópia do backup não passou na verificação; nada foi gravado.");
                }
                conferencia.verificarCifragemAtiva();
            }
            Files.createDirectories(alvo.getParent());
            parcial = Files.createTempFile(alvo.getParent(), "backup-", ".parcial");
            try (OutputStream saida = Files.newOutputStream(parcial)) {
                saida.write(cabecalho(ITERACOES, sal));
                Files.copy(copia, saida);
            }
            moverSemSobrescrever(parcial, alvo);
        } catch (IOException e) {
            throw new BancoException("Não foi possível gravar o backup.");
        } finally {
            Arrays.fill(chaveAtual, (byte) 0);
            Arrays.fill(derivada, (byte) 0);
            zerar(senha);
            limpar(copia, pasta);
            if (parcial != null) {
                try {
                    Files.deleteIfExists(parcial);
                } catch (IOException ignorada) {
                    // arquivo parcial vazio de conteúdo útil; o sistema o limpa depois
                }
            }
        }
    }

    /**
     * Abre o backup com a senha, verifica a integridade e devolve, dentro de {@code pastaTemporaria}, uma cópia
     * recifrada com {@code chaveDaInstalacao}, pronta para {@link BackupLocal#restaurar}. Senha errada, arquivo
     * adulterado ou truncado resultam sempre na mesma mensagem e não alteram nada.
     */
    public static Path prepararRestauracao(Path arquivo, char[] senha, byte[] chaveDaInstalacao, Path pastaTemporaria) {
        byte[] sal = new byte[TAMANHO_DO_SAL];
        int iteracoes;
        byte[] derivada = null;
        Path preparado = null;
        try {
            iteracoes = lerCabecalho(arquivo, sal);
            derivada = derivar(senha, sal, iteracoes);
            Files.createDirectories(pastaTemporaria);
            preparado = Files.createTempFile(pastaTemporaria, "restauracao-preparada-", ".db");
            try (InputStream entrada = Files.newInputStream(arquivo); OutputStream saida = Files.newOutputStream(preparado)) {
                entrada.skipNBytes(TAMANHO_DO_CABECALHO);
                entrada.transferTo(saida);
            }
            try (BancoLocal c = abrirOuFalhar(preparado, derivada)) {
                if (!c.verificarIntegridade().isEmpty()) {
                    throw new BancoException("O backup está corrompido; nada foi alterado.");
                }
                c.trocarChave(chaveDaInstalacao);
            }
            Path pronto = preparado;
            preparado = null;
            return pronto;
        } catch (IOException e) {
            throw new BancoException(MENSAGEM_ABERTURA);
        } finally {
            if (derivada != null) {
                Arrays.fill(derivada, (byte) 0);
            }
            zerar(senha);
            if (preparado != null) {
                BancoLocal.excluirArquivos(preparado);
            }
        }
    }

    /** Restaura um backup protegido por senha em {@code bancoDestino} (que deve estar fechado). */
    public static void restaurar(Path arquivo, char[] senha, Path bancoDestino, byte[] chaveDaInstalacao) {
        Path pronto = prepararRestauracao(arquivo, senha, chaveDaInstalacao, bancoDestino.toAbsolutePath().getParent());
        try {
            BackupLocal.restaurar(pronto, bancoDestino, chaveDaInstalacao);
        } finally {
            BancoLocal.excluirArquivos(pronto);
        }
    }

    private static BancoLocal abrirOuFalhar(Path arquivo, byte[] chave) {
        try {
            return BancoLocal.abrirParaVerificacao(arquivo, chave);
        } catch (BancoException e) {
            throw new BancoException(MENSAGEM_ABERTURA);
        }
    }

    private static int lerCabecalho(Path arquivo, byte[] salDestino) throws IOException {
        if (!Files.isRegularFile(arquivo) || Files.size(arquivo) <= TAMANHO_DO_CABECALHO) {
            throw new BancoException(MENSAGEM_ABERTURA);
        }
        byte[] c = new byte[TAMANHO_DO_CABECALHO];
        try (InputStream in = Files.newInputStream(arquivo)) {
            if (in.readNBytes(c, 0, c.length) != c.length) {
                throw new BancoException(MENSAGEM_ABERTURA);
            }
        }
        if (!Arrays.equals(Arrays.copyOf(c, MAGICA.length), MAGICA) || c[MAGICA.length] != VERSAO) {
            throw new BancoException(MENSAGEM_ABERTURA);
        }
        int iteracoes = ByteBuffer.wrap(c, MAGICA.length + 1, 4).getInt();
        // limites: impedem que um arquivo hostil force um custo absurdo ou reduza o custo de adivinhação
        if (iteracoes < ITERACOES_MINIMAS || iteracoes > ITERACOES_MAXIMAS) {
            throw new BancoException(MENSAGEM_ABERTURA);
        }
        System.arraycopy(c, MAGICA.length + 5, salDestino, 0, TAMANHO_DO_SAL);
        return iteracoes;
    }

    private static byte[] cabecalho(int iteracoes, byte[] sal) {
        ByteBuffer b = ByteBuffer.allocate(TAMANHO_DO_CABECALHO);
        b.put(MAGICA).put(VERSAO).putInt(iteracoes).put(sal);
        return b.array();
    }

    private static void validarSenha(char[] senha) {
        if (senha == null || senha.length < TAMANHO_MINIMO_DA_SENHA) {
            zerar(senha);
            throw new IllegalArgumentException("A senha do backup deve ter pelo menos " + TAMANHO_MINIMO_DA_SENHA + " caracteres.");
        }
        if (senha.length > 256) {
            zerar(senha);
            throw new IllegalArgumentException("A senha do backup é longa demais.");
        }
    }

    private static byte[] derivar(char[] senha, byte[] sal, int iteracoes) {
        // NFC: a mesma senha digitada por métodos de entrada diferentes deve derivar a mesma chave
        char[] normalizada = Normalizer.normalize(new String(senha), Normalizer.Form.NFC).toCharArray();
        PBEKeySpec especificacao = new PBEKeySpec(normalizada, sal, iteracoes, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacao).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponível neste ambiente.", e);
        } finally {
            especificacao.clearPassword();
            Arrays.fill(normalizada, '\0');
        }
    }

    private static void moverSemSobrescrever(Path origem, Path destino) throws IOException {
        if (Files.exists(destino)) {
            throw new BancoException("O arquivo de backup já existe.");
        }
        try {
            Files.move(origem, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origem, destino);
        }
    }

    private static void limpar(Path copia, Path pasta) {
        if (copia != null) {
            BancoLocal.excluirArquivos(copia);
        }
        if (pasta != null) {
            try {
                Files.deleteIfExists(pasta);
            } catch (IOException ignorada) {
                // pasta temporária vazia; o sistema a limpa depois
            }
        }
    }

    private static void zerar(char[] senha) {
        if (senha != null) {
            Arrays.fill(senha, '\0');
        }
    }
}
