package br.com.instadino.servidor.servico;

import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.contratos.licenca.PayloadDeLicenca;
import br.com.instadino.servidor.config.PropriedadesDoServidor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Assina licenças com Ed25519. A chave privada nunca sai do servidor e nunca é versionada:
 * <ul>
 *   <li>produção: lida do arquivo indicado em {@code instadino.licenca.chave-privada-arquivo} (fora do repositório);</li>
 *   <li>desenvolvimento: gerada na primeira execução em {@code <dados>/chaves-dev/} (ignorada pelo Git); o desktop
 *       confia na chave pública correspondente somente se o desenvolvedor a indicar explicitamente;</li>
 *   <li>teste: gerada em memória.</li>
 * </ul>
 */
@Component
public class AssinadorDeLicencas {

    private final PrivateKey privada;
    private final PublicKey publica;
    private final String kid;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    AssinadorDeLicencas(PropriedadesDoServidor propriedades, Environment ambiente) {
        this.kid = propriedades.licenca().kid();
        try {
            Path arquivo = propriedades.licenca().chavePrivadaArquivo();
            if (arquivo != null) {
                this.privada = lerChavePrivada(arquivo);
                this.publica = null;
            } else if (ambiente.acceptsProfiles(Profiles.of("producao"))) {
                throw new IllegalStateException("A chave privada de assinatura de licenças é obrigatória em produção.");
            } else {
                KeyPair par = ambiente.acceptsProfiles(Profiles.of("desenvolvimento")) ? chaveDeDesenvolvimento(propriedades.dados()) : gerar();
                this.privada = par.getPrivate();
                this.publica = par.getPublic();
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível carregar a chave de assinatura de licenças.");
        }
    }

    public String kid() {
        return kid;
    }

    /** Chave pública (apenas fora de produção, para testes e para configurar o desktop de desenvolvimento). */
    public PublicKey chavePublica() {
        return publica;
    }

    public LicencaAssinada assinar(PayloadDeLicenca payload) {
        try {
            byte[] bytes = json.writeValueAsBytes(payload);
            Signature assinatura = Signature.getInstance("Ed25519");
            assinatura.initSign(privada);
            assinatura.update(bytes);
            Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
            return new LicencaAssinada(b64.encodeToString(bytes), b64.encodeToString(assinatura.sign()), kid, LicencaAssinada.ALGORITMO);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Falha ao assinar a licença.");
        }
    }

    private static KeyPair gerar() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static KeyPair chaveDeDesenvolvimento(Path dados) throws IOException, GeneralSecurityException {
        Path pasta = dados.resolve("chaves-dev");
        Path privadaPem = pasta.resolve("licenca-dev.key");
        Path publicaPem = pasta.resolve("licenca-dev.pub");
        KeyFactory fabrica = KeyFactory.getInstance("Ed25519");
        if (Files.isRegularFile(privadaPem) && Files.isRegularFile(publicaPem)) {
            PrivateKey priv = lerChavePrivada(privadaPem);
            PublicKey pub = fabrica.generatePublic(new java.security.spec.X509EncodedKeySpec(decodificarPem(Files.readString(publicaPem))));
            return new KeyPair(pub, priv);
        }
        KeyPair par = gerar();
        Files.createDirectories(pasta);
        Files.writeString(privadaPem, pem("PRIVATE KEY", par.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
        Files.writeString(publicaPem, pem("PUBLIC KEY", par.getPublic().getEncoded()), StandardCharsets.US_ASCII);
        try {
            Files.setPosixFilePermissions(privadaPem, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            // sem POSIX (Windows): depende das permissões da pasta do usuário
        }
        return par;
    }

    private static PrivateKey lerChavePrivada(Path arquivo) throws IOException, GeneralSecurityException {
        byte[] der = decodificarPem(Files.readString(arquivo));
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String pem(String tipo, byte[] der) {
        return "-----BEGIN " + tipo + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + tipo + "-----\n";
    }

    private static byte[] decodificarPem(String texto) {
        String limpo = texto.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(limpo);
    }
}
