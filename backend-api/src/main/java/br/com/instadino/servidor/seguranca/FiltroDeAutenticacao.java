package br.com.instadino.servidor.seguranca;

import br.com.instadino.servidor.servico.ServicoDeSessoes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Lê "Authorization: Bearer ..." e, se a sessão for válida, estabelece a identidade a partir do banco. */
public final class FiltroDeAutenticacao extends OncePerRequestFilter {

    private final ServicoDeSessoes sessoes;

    public FiltroDeAutenticacao(ServicoDeSessoes sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        String cabecalho = requisicao.getHeader("Authorization");
        if (cabecalho != null && cabecalho.regionMatches(true, 0, "Bearer ", 0, 7)) {
            sessoes.autenticar(cabecalho.substring(7).strip()).ifPresent(p -> {
                var autenticacao = new UsernamePasswordAuthenticationToken(p, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + p.papel().name())));
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            });
        }
        cadeia.doFilter(requisicao, resposta);
    }
}
