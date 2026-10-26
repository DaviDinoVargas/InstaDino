package br.com.instadino.servidor.web;

import br.com.instadino.servidor.dominio.EventoDeAuditoria;
import br.com.instadino.servidor.seguranca.PrincipalAutenticado;
import br.com.instadino.servidor.servico.ServicoDeAdministracao;
import br.com.instadino.servidor.servico.ServicoDeAdministracao.ContaAdministrativa;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints exclusivos do papel MASTER (a regra também é imposta em ConfiguracaoDeSeguranca). */
@RestController
class ControladorAdministrativo {

    record RequisicaoDeConcessao(UUID contaId, Integer dias, String motivo) {
    }

    record RequisicaoDePlano(String id, String nome, String descricao, String periodicidade, long precoEmCentavos) {
    }

    record ItemDeAuditoria(Long id, Instant ocorridoEm, UUID atorId, String atorTipo, String acao, String alvo, String detalhe) {
    }

    private final ServicoDeAdministracao administracao;

    ControladorAdministrativo(ServicoDeAdministracao administracao) {
        this.administracao = administracao;
    }

    @GetMapping("/v1/admin/contas")
    List<ContaAdministrativa> contas() {
        return administracao.listarContas();
    }

    @PostMapping("/v1/admin/concessoes")
    Map<String, UUID> conceder(@AuthenticationPrincipal PrincipalAutenticado ator, @RequestBody RequisicaoDeConcessao r) {
        return Map.of("id", administracao.conceder(ator.contaId(), r.contaId(), r.dias(), r.motivo()));
    }

    @DeleteMapping("/v1/admin/concessoes/{id}")
    ResponseEntity<Void> revogarConcessao(@AuthenticationPrincipal PrincipalAutenticado ator, @PathVariable UUID id) {
        administracao.revogarConcessao(ator.contaId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/admin/contas/{id}/revogar-sessoes")
    ResponseEntity<Void> revogarSessoes(@AuthenticationPrincipal PrincipalAutenticado ator, @PathVariable UUID id) {
        administracao.revogarSessoes(ator.contaId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/admin/contas/{id}/revogar-licencas")
    ResponseEntity<Void> revogarLicencas(@AuthenticationPrincipal PrincipalAutenticado ator, @PathVariable UUID id) {
        administracao.revogarLicencas(ator.contaId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/admin/contas/{id}/desativar")
    ResponseEntity<Void> desativar(@AuthenticationPrincipal PrincipalAutenticado ator, @PathVariable UUID id) {
        administracao.desativarConta(ator.contaId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/admin/planos")
    ResponseEntity<Void> plano(@AuthenticationPrincipal PrincipalAutenticado ator, @RequestBody RequisicaoDePlano r) {
        administracao.criarPlano(ator.contaId(), r.id(), r.nome(), r.descricao(), r.periodicidade(), r.precoEmCentavos());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/admin/auditoria")
    List<ItemDeAuditoria> auditoria() {
        return administracao.auditoriaRecente().stream().map((EventoDeAuditoria e) -> new ItemDeAuditoria(e.getId(), e.getOcorridoEm(),
                e.getAtorId(), e.getAtorTipo(), e.getAcao(), e.getAlvo(), e.getDetalhe())).toList();
    }
}
