package br.com.instadino.servidor.web;

import br.com.instadino.contratos.licenca.DispositivoAutorizado;
import br.com.instadino.contratos.licenca.LicencaAssinada;
import br.com.instadino.contratos.licenca.RequisicaoDeLicenca;
import br.com.instadino.contratos.pagamento.EstadoDaAssinatura;
import br.com.instadino.servidor.seguranca.PrincipalAutenticado;
import br.com.instadino.servidor.servico.ServicoDeDireitos;
import br.com.instadino.servidor.servico.ServicoDeLicencas;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Licenças assinadas, dispositivos e situação comercial da conta autenticada. */
@RestController
class ControladorDeLicencas {

    private final ServicoDeLicencas licencas;
    private final ServicoDeDireitos direitos;

    ControladorDeLicencas(ServicoDeLicencas licencas, ServicoDeDireitos direitos) {
        this.licencas = licencas;
        this.direitos = direitos;
    }

    @PostMapping("/v1/licencas")
    LicencaAssinada ativarOuRenovar(@AuthenticationPrincipal PrincipalAutenticado principal, @RequestBody RequisicaoDeLicenca requisicao) {
        return licencas.ativarOuRenovar(principal.contaId(), requisicao.idDoDispositivo(), requisicao.nomeDoDispositivo());
    }

    @GetMapping("/v1/dispositivos")
    List<DispositivoAutorizado> dispositivos(@AuthenticationPrincipal PrincipalAutenticado principal) {
        return licencas.dispositivosDa(principal.contaId());
    }

    @DeleteMapping("/v1/dispositivos/{id}")
    ResponseEntity<Void> removerDispositivo(@AuthenticationPrincipal PrincipalAutenticado principal, @PathVariable UUID id) {
        licencas.removerDispositivo(principal.contaId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/assinatura")
    EstadoDaAssinatura assinatura(@AuthenticationPrincipal PrincipalAutenticado principal) {
        return direitos.estadoDa(principal.contaId());
    }
}
