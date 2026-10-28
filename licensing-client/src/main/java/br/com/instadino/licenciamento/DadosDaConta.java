package br.com.instadino.licenciamento;

import br.com.instadino.contratos.conta.ResumoDaConta;
import br.com.instadino.contratos.licenca.LicencaAssinada;
import java.time.Instant;

/**
 * Conteúdo do cache local da conta comercial. É gravado protegido pela DPAPI e nunca é a prova de um plano:
 * a licença nele contida é revalidada criptograficamente a cada uso.
 */
public record DadosDaConta(String token, Instant tokenExpiraEm, ResumoDaConta conta, LicencaAssinada licenca, Instant ultimoInstanteVisto) {

    public DadosDaConta comLicenca(LicencaAssinada nova, Instant visto) {
        return new DadosDaConta(token, tokenExpiraEm, conta, nova, visto);
    }

    public DadosDaConta comVisto(Instant visto) {
        return new DadosDaConta(token, tokenExpiraEm, conta, licenca, visto);
    }

    @Override
    public String toString() {
        return "DadosDaConta[token=***]";
    }
}
