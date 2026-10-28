package br.com.instadino.fixtures;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Fabrica arquivos ZIP hostis e corrompidos para testar o importador. Nenhum deles contém dados reais. */
public final class ZipsMaliciosos {

    private ZipsMaliciosos() {
    }

    /** ZIP com uma entrada de nome arbitrário (sem validação do JDK). */
    public static byte[] comEntrada(String nome, byte[] conteudo) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry(nome));
            zip.write(conteudo);
            zip.closeEntry();
            zip.finish();
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] caminhoComPontosPontos() {
        return comEntrada("../../etc/instadino-fora.json", "[]".getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] caminhoAbsolutoUnix() {
        return comEntrada("/tmp/instadino-absoluto.json", "[]".getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] caminhoAbsolutoWindows() {
        return comEntrada("C:/Windows/instadino.json", "[]".getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] caminhoComBarraInvertida() {
        return comEntrada("connections\\..\\..\\fora.json", "[]".getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] caminhoComByteNulo() {
        return comEntrada("connections/followers\u0000.json", "[]".getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] caminhoMuitoProfundo(int niveis) {
        return comEntrada("a/".repeat(niveis) + "x.json", "[]".getBytes(StandardCharsets.UTF_8));
    }

    /** Duas entradas que, depois da normalização, apontam para o mesmo caminho (diferença só de maiúsculas). */
    public static byte[] duplicadasPorCaixa() {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry("connections/followers_and_following/followers_1.json"));
            zip.write("[]".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("Connections/Followers_and_Following/FOLLOWERS_1.json"));
            zip.write("[]".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Duas entradas com nome idêntico (o JDK não permite criar; os nomes são igualados nos bytes). */
    public static byte[] duplicadasExatas() {
        byte[] bytes;
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry("dados/aaaa.json"));
            zip.write("[]".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("dados/bbbb.json"));
            zip.write("[]".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            bytes = saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] de = "dados/bbbb.json".getBytes(StandardCharsets.UTF_8);
        byte[] para = "dados/aaaa.json".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i <= bytes.length - de.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(bytes, i, i + de.length), de)) {
                System.arraycopy(para, 0, bytes, i, para.length);
            }
        }
        return bytes;
    }

    /** ZIP bomb em miniatura: poucos KB que se expandem para {@code megabytes} MB de zeros. */
    public static byte[] bombaDeCompressao(int megabytes) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry("connections/followers_and_following/followers_1.json"));
            byte[] bloco = new byte[1024 * 1024];
            Arrays.fill(bloco, (byte) ' ');
            zip.write("[".getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < megabytes; i++) {
                zip.write(bloco);
            }
            zip.write("]".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] muitasEntradas(int quantidade) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(saida)) {
            for (int i = 0; i < quantidade; i++) {
                zip.putNextEntry(new ZipEntry("media/posts/f" + i + ".jpg"));
                zip.closeEntry();
            }
            zip.finish();
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Remove o final do arquivo, destruindo o diretório central. */
    public static byte[] truncado(byte[] zipValido) {
        return Arrays.copyOf(zipValido, zipValido.length / 2);
    }

    public static byte[] naoEhZip() {
        return "Isto não é um arquivo ZIP.".getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] vazio() {
        return new byte[0];
    }
}
