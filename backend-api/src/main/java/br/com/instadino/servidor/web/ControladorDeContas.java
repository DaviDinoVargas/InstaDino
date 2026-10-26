package br.com.instadino.servidor.web;

import br.com.instadino.contratos.conta.RequisicaoDeCadastro;
import br.com.instadino.contratos.conta.RequisicaoDeLogin;
import br.com.instadino.contratos.conta.RequisicaoDeRecuperacao;
import br.com.instadino.contratos.conta.RequisicaoDeRedefinicao;
import br.com.instadino.contratos.conta.RequisicaoDeToken;
import br.com.instadino.contratos.conta.RespostaDeLogin;
import br.com.instadino.contratos.conta.ResumoDaConta;
import br.com.instadino.contratos.conta.SessaoAtiva;
import br.com.instadino.servidor.seguranca.PrincipalAutenticado;
import br.com.instadino.servidor.servico.ServicoDeContas;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Contas, sessões e recuperação de senha. */
@RestController
class ControladorDeContas {

    private static final Map<String, String> ACEITO = Map.of("mensagem",
            "Se o e-mail puder ser usado, enviaremos as instruções em instantes.");

    private final ServicoDeContas contas;

    ControladorDeContas(ServicoDeContas contas) {
        this.contas = contas;
    }

    @PostMapping("/v1/contas")
    ResponseEntity<Map<String, String>> cadastrar(@RequestBody RequisicaoDeCadastro requisicao) {
        contas.cadastrar(requisicao);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ACEITO);
    }

    @PostMapping("/v1/contas/verificar-email")
    ResponseEntity<Void> verificarEmail(@RequestBody RequisicaoDeToken requisicao) {
        contas.verificarEmail(requisicao.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/sessoes")
    RespostaDeLogin entrar(@RequestBody RequisicaoDeLogin requisicao) {
        return contas.login(requisicao);
    }

    @DeleteMapping("/v1/sessoes/atual")
    ResponseEntity<Void> sair(@AuthenticationPrincipal PrincipalAutenticado principal) {
        contas.sair(principal.contaId(), principal.sessaoId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/sessoes")
    List<SessaoAtiva> sessoes(@AuthenticationPrincipal PrincipalAutenticado principal) {
        return contas.listarSessoes(principal.contaId(), principal.sessaoId());
    }

    @DeleteMapping("/v1/sessoes/{id}")
    ResponseEntity<Void> revogar(@AuthenticationPrincipal PrincipalAutenticado principal, @PathVariable UUID id) {
        contas.revogarSessao(principal.contaId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/senha/recuperacao")
    ResponseEntity<Map<String, String>> recuperar(@RequestBody RequisicaoDeRecuperacao requisicao) {
        contas.solicitarRecuperacao(requisicao.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ACEITO);
    }

    @PostMapping("/v1/senha/redefinir")
    ResponseEntity<Void> redefinir(@RequestBody RequisicaoDeRedefinicao requisicao) {
        contas.redefinirSenha(requisicao.token(), requisicao.novaSenha());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/conta")
    ResumoDaConta minhaConta(@AuthenticationPrincipal PrincipalAutenticado principal) {
        return contas.resumoDaConta(principal.contaId());
    }

    @GetMapping("/v1/status")
    Map<String, String> status() {
        return Map.of("servico", "instadino", "estado", "ok");
    }
}
