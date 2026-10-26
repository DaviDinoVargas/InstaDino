package br.com.instadino.servidor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.instadino.contratos.conta.Papel;
import br.com.instadino.servidor.dominio.Conta;
import br.com.instadino.servidor.dominio.Repositorios.RepositorioDeContas;
import br.com.instadino.servidor.email.MensagemDeEmail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("teste")
@Import(ConfiguracaoDeTeste.class)
public abstract class TesteBase {

    public static final String SENHA_FORTE = "Uma-senha-bem-longa-42!";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected RelogioMutavel relogio;
    @Autowired
    protected EmailEmMemoria emails;
    @Autowired
    protected RepositorioDeContas contas;
    @Autowired
    protected PasswordEncoder codificador;

    protected static String emailUnico() {
        return "usuario-" + UUID.randomUUID().toString().substring(0, 8) + "@instadino.test";
    }

    protected static String dispositivo() {
        return UUID.randomUUID().toString();
    }

    protected Conta criarConta(String email, String senha, Papel papel, boolean verificada) {
        return contas.save(new Conta(email, email, "Pessoa de Teste", codificador.encode(senha), papel, verificada, relogio.instant()));
    }

    protected MockHttpServletRequestBuilder postJson(String caminho, Object corpo) throws Exception {
        return post(caminho).contentType(MediaType.APPLICATION_JSON).content(corpo instanceof String s ? s : json.writeValueAsString(corpo));
    }

    protected MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    protected MvcResult executar(MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b).andReturn();
    }

    protected JsonNode corpo(MvcResult r) throws Exception {
        String texto = r.getResponse().getContentAsString();
        return texto.isEmpty() ? json.createObjectNode() : json.readTree(texto);
    }

    /** Faz login e devolve o token de sessão. */
    protected String entrar(String email, String senha) throws Exception {
        MvcResult r = executar(postJson("/v1/sessoes", Map.of("email", email, "senha", senha, "idDoDispositivo", dispositivo(), "nomeDoDispositivo", "PC de teste")));
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("Login falhou: " + r.getResponse().getStatus() + " " + r.getResponse().getContentAsString());
        }
        return corpo(r).get("token").asText();
    }

    protected String tokenDoEmail(String email, MensagemDeEmail.Tipo tipo) {
        return emails.ultima(email, tipo).token();
    }

    protected MockHttpServletRequestBuilder obter(String caminho, String token) {
        return comToken(get(caminho), token);
    }

    protected MockHttpServletRequestBuilder apagar(String caminho, String token) {
        return comToken(delete(caminho), token);
    }
}
