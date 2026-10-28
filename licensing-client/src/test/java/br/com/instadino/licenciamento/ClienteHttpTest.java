package br.com.instadino.licenciamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClienteHttpTest {

    private HttpServer servidor;

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private URI iniciar(String caminho, int status, String corpo, AtomicReference<String> autorizacao) throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext(caminho, troca -> {
            if (autorizacao != null) {
                autorizacao.set(troca.getRequestHeaders().getFirst("Authorization"));
            }
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                troca.getResponseBody().write(bytes);
            }
            troca.close();
        });
        servidor.start();
        return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    @Test
    void exigeHttpsExcetoLoopbackExplicitamenteHabilitado() {
        new ClienteHttp(URI.create("https://api.instadino.exemplo"), false);
        assertThatThrownBy(() -> new ClienteHttp(URI.create("http://api.instadino.exemplo"), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClienteHttp(URI.create("http://127.0.0.1:8443"), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClienteHttp(URI.create("http://localhost.evil.com"), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClienteHttp(URI.create("ftp://127.0.0.1"), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClienteHttp(URI.create("file:///etc/passwd"), true)).isInstanceOf(IllegalArgumentException.class);
        new ClienteHttp(URI.create("http://127.0.0.1:8443"), true);
        new ClienteHttp(URI.create("http://localhost:8443"), true);
    }

    @Test
    void enviaOTokenSomenteNoCabecalhoAuthorization() throws Exception {
        var recebido = new AtomicReference<String>();
        URI base = iniciar("/v1/conta", 200,
                "{\"id\":\"5f0e2b0a-8d4f-4a52-9e55-6a1f2c3d4e5f\",\"email\":\"a@b.test\",\"nome\":\"A\",\"papel\":\"CLIENTE\",\"emailVerificado\":true}", recebido);
        var conta = new ClienteHttp(base, true).conta("token-de-teste-1234567890");
        assertThat(recebido.get()).isEqualTo("Bearer token-de-teste-1234567890");
        assertThat(conta.email()).isEqualTo("a@b.test");
    }

    @Test
    void errosDaApiViramExcecaoComCodigoEMensagemSeguros() throws Exception {
        URI base = iniciar("/v1/licencas", 403, "{\"codigo\":\"LICENCA_INDISPONIVEL\",\"mensagem\":\"Sem assinatura.\"}", null);
        assertThatThrownBy(() -> new ClienteHttp(base, true).ativarLicenca("t", "dispositivo-teste-1", "PC"))
                .isInstanceOfSatisfying(ErroDaApi.class, e -> {
                    assertThat(e.status()).isEqualTo(403);
                    assertThat(e.codigo()).isEqualTo("LICENCA_INDISPONIVEL");
                });
    }

    @Test
    void respostaDeErroSemFormatoEsperadoNaoVazaCorpo() throws Exception {
        URI base = iniciar("/v1/conta", 500, "<html>Exception at br.com.segredo.Classe line 42</html>", null);
        assertThatThrownBy(() -> new ClienteHttp(base, true).conta("t")).isInstanceOfSatisfying(ErroDaApi.class,
                e -> assertThat(e.getMessage()).doesNotContain("Exception", "segredo", "html"));
    }

    @Test
    void respostaEnormeERecusada() throws Exception {
        URI base = iniciar("/v1/conta", 200, "{\"x\":\"" + "a".repeat(2 * 1024 * 1024) + "\"}", null);
        assertThatThrownBy(() -> new ClienteHttp(base, true).conta("t")).isInstanceOf(ErroDaApi.class);
    }

    @Test
    void jsonInvalidoNaoViraExcecaoDesconhecida() throws Exception {
        URI base = iniciar("/v1/conta", 200, "isto não é json", null);
        assertThatThrownBy(() -> new ClienteHttp(base, true).conta("t")).isInstanceOf(ErroDaApi.class);
    }

    @Test
    void redirecionamentosNaoSaoSeguidos() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/conta", troca -> {
            troca.getResponseHeaders().add("Location", "http://127.0.0.1:1/roubo");
            troca.sendResponseHeaders(302, -1);
            troca.close();
        });
        servidor.start();
        URI base = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
        assertThatThrownBy(() -> new ClienteHttp(base, true).conta("segredo-que-nao-pode-vazar")).isInstanceOf(ErroDaApi.class);
    }

    @Test
    void servidorForaDoArViraFalhaDeRede() {
        assertThatThrownBy(() -> new ClienteHttp(URI.create("http://127.0.0.1:1"), true).catalogo()).isInstanceOf(FalhaDeRede.class);
    }
}
