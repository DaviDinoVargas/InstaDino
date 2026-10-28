package br.com.instadino.servidor.seguranca;

import br.com.instadino.contratos.conta.ErroApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita a taxa dos endpoints públicos sensíveis (cadastro, login, recuperação, verificação) por endereço do cliente,
 * e rejeita corpos grandes demais antes de qualquer processamento.
 */
public final class FiltroDeLimiteDeTaxa extends OncePerRequestFilter {

    private static final Set<String> CAMINHOS_LIMITADOS = Set.of("/v1/contas", "/v1/contas/verificar-email", "/v1/sessoes",
            "/v1/senha/recuperacao", "/v1/senha/redefinir");
    private static final long TAMANHO_MAXIMO_DO_CORPO = 64 * 1024;

    private final LimitadorDeTaxa limitador;
    private final boolean confiarEmProxy;
    private final ObjectMapper json;

    public FiltroDeLimiteDeTaxa(LimitadorDeTaxa limitador, boolean confiarEmProxy, ObjectMapper json) {
        this.limitador = limitador;
        this.confiarEmProxy = confiarEmProxy;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        if (requisicao.getContentLengthLong() > TAMANHO_MAXIMO_DO_CORPO && !requisicao.getRequestURI().startsWith("/v1/webhooks/")) {
            escrever(resposta, 413, new ErroApi("CORPO_GRANDE_DEMAIS", "A requisição é maior que o permitido."));
            return;
        }
        if ("POST".equals(requisicao.getMethod()) && CAMINHOS_LIMITADOS.contains(requisicao.getRequestURI())) {
            String chave = enderecoDoCliente(requisicao) + "|" + requisicao.getRequestURI();
            if (!limitador.permitir(chave)) {
                resposta.setHeader("Retry-After", Long.toString(limitador.segundosAteLiberar(chave)));
                escrever(resposta, 429, new ErroApi("MUITAS_REQUISICOES", "Muitas tentativas. Aguarde alguns minutos e tente novamente."));
                return;
            }
        }
        cadeia.doFilter(requisicao, resposta);
    }

    private String enderecoDoCliente(HttpServletRequest requisicao) {
        if (confiarEmProxy) {
            String encaminhado = requisicao.getHeader("X-Forwarded-For");
            if (encaminhado != null && !encaminhado.isBlank()) {
                return encaminhado.split(",")[0].strip();
            }
        }
        return requisicao.getRemoteAddr();
    }

    private void escrever(HttpServletResponse resposta, int status, ErroApi erro) throws IOException {
        resposta.setStatus(status);
        resposta.setContentType("application/json");
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.getWriter().write(json.writeValueAsString(erro));
    }
}
