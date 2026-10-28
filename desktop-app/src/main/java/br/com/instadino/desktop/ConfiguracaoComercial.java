package br.com.instadino.desktop;

import br.com.instadino.licenciamento.ApiComercial;
import br.com.instadino.licenciamento.ArmazemDeConta;
import br.com.instadino.licenciamento.ChavesConfiaveis;
import br.com.instadino.licenciamento.ClienteHttp;
import br.com.instadino.licenciamento.GerenciadorDeLicenca;
import br.com.instadino.licenciamento.IdDoDispositivo;
import br.com.instadino.licenciamento.VerificadorDeLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorDeSegredo;
import br.com.instadino.persistencia.seguranca.ProtetoresPadrao;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Monta o gerenciador de licença a partir do ambiente. O serviço comercial só é usado se {@code INSTADINO_API} estiver
 * definida; sem ela o aplicativo funciona apenas com o plano Free. TLS é obrigatório, exceto loopback no modo de
 * desenvolvimento. Chaves públicas extras (desenvolvimento) só são aceitas no modo de desenvolvimento.
 */
final class ConfiguracaoComercial {

    private ConfiguracaoComercial() {
    }

    static GerenciadorDeLicenca criar(Path dados, ProtetorDeSegredo protetor, Clock relogio) {
        boolean desenvolvimento = Boolean.getBoolean(ProtetoresPadrao.PROPRIEDADE_MODO_INSEGURO);
        String endereco = System.getenv("INSTADINO_API");
        ApiComercial api = null;
        if (endereco != null && !endereco.isBlank()) {
            try {
                api = new ClienteHttp(URI.create(endereco.strip()), desenvolvimento);
            } catch (IllegalArgumentException e) {
                System.getLogger(ConfiguracaoComercial.class.getName()).log(System.Logger.Level.WARNING,
                        "INSTADINO_API ignorada: o serviço comercial exige HTTPS.");
            }
        }
        ChavesConfiaveis chaves = ChavesConfiaveis.embutidas();
        String pastaDeDesenvolvimento = System.getenv("INSTADINO_CHAVES_CONFIAVEIS");
        if (desenvolvimento && pastaDeDesenvolvimento != null && !pastaDeDesenvolvimento.isBlank()) {
            chaves = chaves.comPastaDeDesenvolvimento(Path.of(pastaDeDesenvolvimento.strip()));
        }
        String idDoDispositivo = IdDoDispositivo.obterOuCriar(dados.resolve("dispositivo.id"));
        String nome = "Computador com " + (System.getProperty("os.name", "sistema").startsWith("Windows") ? "Windows" : System.getProperty("os.name", "sistema"))
                + " (" + idDoDispositivo.substring(0, 4) + ")";
        return new GerenciadorDeLicenca(api, new ArmazemDeConta(dados.resolve("conta-comercial.cache"), protetor),
                new VerificadorDeLicenca(chaves), relogio, idDoDispositivo, nome);
    }
}
