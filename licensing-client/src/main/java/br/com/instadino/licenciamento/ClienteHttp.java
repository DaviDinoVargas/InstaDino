package br.com.instadino.licenciamento;

import br.com.instadino.contratos.conta.ErroApi;
import br.com.instadino.contratos.conta.RespostaDeLogin;
import br.com.instadino.contratos.conta.ResumoDaConta;
import br.com.instadino.contratos.conta.SessaoAtiva;
import br.com.instadino.contratos.licenca.DispositivoAutorizado;
import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.contratos.pagamento.EstadoDaAssinatura;
import br.com.instadino.contratos.pagamento.ItemDoHistorico;
import br.com.instadino.contratos.pagamento.RespostaDeCatalogo;
import br.com.instadino.contratos.pagamento.RespostaDeCheckout;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente HTTP da API comercial. TLS é obrigatório: HTTP simples só é aceito para o endereço de loopback
 * quando o desenvolvedor habilita isso explicitamente. Redirecionamentos não são seguidos. O token de sessão é
 * enviado apenas no cabeçalho Authorization e nunca é registrado em log.
 */
public final class ClienteHttp implements ApiComercial {

    private static final int LIMITE_DA_RESPOSTA = 1024 * 1024;

    private final URI base;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ClienteHttp(URI base, boolean permitirHttpEmLoopback) {
        validarBase(base, permitirHttpEmLoopback);
        this.base = base;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    static void validarBase(URI base, boolean permitirHttpEmLoopback) {
        String esquema = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        String host = base.getHost() == null ? "" : base.getHost().toLowerCase(Locale.ROOT);
        boolean loopback = host.equals("127.0.0.1") || host.equals("localhost") || host.equals("[::1]") || host.equals("::1");
        if (esquema.equals("https")) {
            return;
        }
        if (esquema.equals("http") && loopback && permitirHttpEmLoopback) {
            return;
        }
        throw new IllegalArgumentException("O serviço comercial exige HTTPS.");
    }

    // ---- conta ----

    @Override
    public void cadastrar(String email, String senha, String nome) {
        chamar("POST", "/v1/contas", null, Map.of("email", email, "senha", senha, "nome", nome), null);
    }

    @Override
    public void verificarEmail(String token) {
        chamar("POST", "/v1/contas/verificar-email", null, Map.of("token", token), null);
    }

    @Override
    public RespostaDeLogin entrar(String email, String senha, String idDoDispositivo, String nomeDoDispositivo) {
        return chamar("POST", "/v1/sessoes", null,
                Map.of("email", email, "senha", senha, "idDoDispositivo", idDoDispositivo, "nomeDoDispositivo", nomeDoDispositivo == null ? "" : nomeDoDispositivo),
                new TypeReference<RespostaDeLogin>() { });
    }

    @Override
    public void sair(String token) {
        chamar("DELETE", "/v1/sessoes/atual", token, null, null);
    }

    @Override
    public ResumoDaConta conta(String token) {
        return chamar("GET", "/v1/conta", token, null, new TypeReference<ResumoDaConta>() { });
    }

    @Override
    public List<SessaoAtiva> sessoes(String token) {
        return chamar("GET", "/v1/sessoes", token, null, new TypeReference<List<SessaoAtiva>>() { });
    }

    @Override
    public void revogarSessao(String token, UUID sessaoId) {
        chamar("DELETE", "/v1/sessoes/" + sessaoId, token, null, null);
    }

    @Override
    public void solicitarRecuperacao(String email) {
        chamar("POST", "/v1/senha/recuperacao", null, Map.of("email", email), null);
    }

    @Override
    public void redefinirSenha(String tokenDeRecuperacao, String novaSenha) {
        chamar("POST", "/v1/senha/redefinir", null, Map.of("token", tokenDeRecuperacao, "novaSenha", novaSenha), null);
    }

    // ---- licença e assinatura ----

    @Override
    public LicencaAssinada ativarLicenca(String token, String idDoDispositivo, String nomeDoDispositivo) {
        return chamar("POST", "/v1/licencas", token,
                Map.of("idDoDispositivo", idDoDispositivo, "nomeDoDispositivo", nomeDoDispositivo == null ? "" : nomeDoDispositivo),
                new TypeReference<LicencaAssinada>() { });
    }

    @Override
    public EstadoDaAssinatura assinatura(String token) {
        return chamar("GET", "/v1/assinatura", token, null, new TypeReference<EstadoDaAssinatura>() { });
    }

    @Override
    public List<DispositivoAutorizado> dispositivos(String token) {
        return chamar("GET", "/v1/dispositivos", token, null, new TypeReference<List<DispositivoAutorizado>>() { });
    }

    @Override
    public void removerDispositivo(String token, UUID dispositivoId) {
        chamar("DELETE", "/v1/dispositivos/" + dispositivoId, token, null, null);
    }

    // ---- pagamentos ----

    @Override
    public RespostaDeCatalogo catalogo() {
        return chamar("GET", "/v1/planos", null, null, new TypeReference<RespostaDeCatalogo>() { });
    }

    @Override
    public RespostaDeCheckout criarCheckout(String token, String planoId) {
        return chamar("POST", "/v1/checkout", token, Map.of("planoId", planoId), new TypeReference<RespostaDeCheckout>() { });
    }

    @Override
    public List<ItemDoHistorico> historico(String token) {
        return chamar("GET", "/v1/pedidos", token, null, new TypeReference<List<ItemDoHistorico>>() { });
    }

    @Override
    public void simularEventoDeSandbox(String token, UUID pedidoId, String tipoDeEvento) {
        chamar("POST", "/v1/sandbox/pedidos/" + pedidoId + "/eventos/" + tipoDeEvento, token, null, null);
    }

    // ---- infraestrutura ----

    private <T> T chamar(String metodo, String caminho, String token, Object corpo, TypeReference<T> tipo) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(base.resolve(caminho)).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            if (corpo != null) {
                b.header("Content-Type", "application/json");
                b.method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo), StandardCharsets.UTF_8));
            } else if (metodo.equals("POST")) {
                b.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                b.method(metodo, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<InputStream> resposta = http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (InputStream in = resposta.body()) {
                bytes = in.readNBytes(LIMITE_DA_RESPOSTA + 1);
            }
            if (bytes.length > LIMITE_DA_RESPOSTA) {
                throw new ErroDaApi(502, "RESPOSTA_INVALIDA", "O serviço comercial devolveu uma resposta inesperada.");
            }
            int status = resposta.statusCode();
            if (status < 200 || status >= 300) {
                throw erro(status, bytes);
            }
            if (tipo == null) {
                return null;
            }
            if (bytes.length == 0) {
                throw new ErroDaApi(502, "RESPOSTA_INVALIDA", "O serviço comercial devolveu uma resposta inesperada.");
            }
            return json.readValue(bytes, tipo);
        } catch (IOException e) {
            throw e instanceof com.fasterxml.jackson.core.JsonProcessingException
                    ? new ErroDaApi(502, "RESPOSTA_INVALIDA", "O serviço comercial devolveu uma resposta inesperada.") : new FalhaDeRede();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaDeRede();
        }
    }

    private ErroDaApi erro(int status, byte[] bytes) {
        try {
            ErroApi e = json.readValue(bytes, ErroApi.class);
            if (e != null && e.codigo() != null) {
                return new ErroDaApi(status, e.codigo(), e.mensagem() == null ? "Operação não concluída." : e.mensagem());
            }
        } catch (IOException ignorada) {
            // resposta de erro sem o formato esperado
        }
        return new ErroDaApi(status, "ERRO_DESCONHECIDO", "Operação não concluída (código " + status + ").");
    }
}
