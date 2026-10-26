package br.com.instadino.servidor.seguranca;

import br.com.instadino.contratos.conta.ErroApi;
import br.com.instadino.servidor.config.PropriedadesDoServidor;
import br.com.instadino.servidor.servico.ServicoDeSessoes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/** API sem estado: sem cookies, sem CSRF, autenticação por token opaco, autorização no servidor. */
@Configuration
class ConfiguracaoDeSeguranca {

    @Bean
    LimitadorDeTaxa limitadorDeTaxa(Clock relogio, PropriedadesDoServidor propriedades) {
        var s = propriedades.seguranca();
        return new LimitadorDeTaxa(relogio, s.limiteDeRequisicoesPorJanela(), Duration.ofMinutes(s.janelaEmMinutos()));
    }

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http, ServicoDeSessoes sessoes, LimitadorDeTaxa limitador,
                                          PropriedadesDoServidor propriedades, ObjectMapper json) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .cacheControl(Customizer.withDefaults()))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/v1/contas", "/v1/contas/verificar-email", "/v1/sessoes",
                                "/v1/senha/recuperacao", "/v1/senha/redefinir", "/v1/webhooks/pagamento").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/planos", "/v1/status").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v1/admin/**").hasRole("MASTER")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> escrever(res, json, 401, new ErroApi("NAO_AUTENTICADO", "Entre na sua conta para continuar.")))
                        .accessDeniedHandler((req, res, ex) -> escrever(res, json, 403, new ErroApi("ACESSO_NEGADO", "Você não tem permissão para esta operação."))))
                .addFilterBefore(new FiltroDeLimiteDeTaxa(limitador, propriedades.seguranca().confiarEmProxy(), json), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new FiltroDeAutenticacao(sessoes), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void escrever(jakarta.servlet.http.HttpServletResponse res, ObjectMapper json, int status, ErroApi erro) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write(json.writeValueAsString(erro));
    }
}
