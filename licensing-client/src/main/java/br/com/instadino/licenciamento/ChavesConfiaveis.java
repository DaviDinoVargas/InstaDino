package br.com.instadino.licenciamento;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Conjunto de chaves PÚBLICAS em que o desktop confia para validar licenças. As chaves de produção ficam embutidas
 * no aplicativo (recurso {@code chaves-publicas/}); em desenvolvimento é possível acrescentar uma pasta explícita.
 * Chaves recebidas do servidor em tempo de execução NUNCA são aceitas.
 */
public final class ChavesConfiaveis {

    private final Map<String, PublicKey> chaves;

    private ChavesConfiaveis(Map<String, PublicKey> chaves) {
        this.chaves = Map.copyOf(chaves);
    }

    public static ChavesConfiaveis vazio() {
        return new ChavesConfiaveis(Map.of());
    }

    public static ChavesConfiaveis de(String kid, PublicKey chave) {
        return new ChavesConfiaveis(Map.of(kid, chave));
    }

    /** Chaves embutidas no aplicativo, listadas em {@code chaves-publicas/indice.txt}. */
    public static ChavesConfiaveis embutidas() {
        Map<String, PublicKey> mapa = new HashMap<>();
        try (InputStream in = ChavesConfiaveis.class.getClassLoader().getResourceAsStream("chaves-publicas/indice.txt")) {
            if (in != null) {
                for (String linha : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    String l = linha.strip();
                    if (l.isEmpty() || l.startsWith("#") || !l.contains("=")) {
                        continue;
                    }
                    String kid = l.substring(0, l.indexOf('=')).strip();
                    String arquivo = l.substring(l.indexOf('=') + 1).strip();
                    try (InputStream pem = ChavesConfiaveis.class.getClassLoader().getResourceAsStream("chaves-publicas/" + arquivo)) {
                        if (pem != null) {
                            mapa.put(kid, lerPem(new String(pem.readAllBytes(), StandardCharsets.UTF_8)));
                        }
                    }
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Chaves de licença embutidas inválidas.");
        }
        return new ChavesConfiaveis(mapa);
    }

    /**
     * SOMENTE DESENVOLVIMENTO: acrescenta chaves {@code <kid>.pub} de uma pasta indicada explicitamente pelo desenvolvedor.
     * Em produção essa pasta não é consultada.
     */
    public ChavesConfiaveis comPastaDeDesenvolvimento(Path pasta) {
        Map<String, PublicKey> mapa = new HashMap<>(chaves);
        if (pasta != null && Files.isDirectory(pasta)) {
            try (Stream<Path> arquivos = Files.list(pasta)) {
                for (Path p : arquivos.filter(x -> x.getFileName().toString().endsWith(".pub")).toList()) {
                    String nome = p.getFileName().toString();
                    String kid = nome.substring(0, nome.length() - 4);
                    mapa.put(kid, lerPem(Files.readString(p)));
                }
            } catch (IOException | GeneralSecurityException e) {
                throw new IllegalStateException("Chave pública de desenvolvimento inválida.");
            }
        }
        return new ChavesConfiaveis(mapa);
    }

    public Optional<PublicKey> chave(String kid) {
        return kid == null ? Optional.empty() : Optional.ofNullable(chaves.get(kid));
    }

    public boolean semChaves() {
        return chaves.isEmpty();
    }

    public int quantidade() {
        return chaves.size();
    }

    static PublicKey lerPem(String pem) throws GeneralSecurityException {
        String limpo = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(limpo)));
    }
}
