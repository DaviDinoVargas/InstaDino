package br.com.instadino.nucleo.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PoliticaDeAcessoTest {

    private static final RegistroDeConsentimento SEM_CONSENTIMENTO = f -> false;
    private static final RegistroDeConsentimento COM_CONSENTIMENTO = f -> true;

    private static PoliticaDeAcesso politica(EstadoDaLicenca estado, RegistroDeConsentimento consentimento) {
        return new PoliticaDeAcesso(() -> estado, consentimento);
    }

    @ParameterizedTest
    @EnumSource(Funcionalidade.class)
    void planoFreePermiteApenasFuncionalidadesFree(Funcionalidade f) {
        var politica = politica(EstadoDaLicenca.GRATUITO, COM_CONSENTIMENTO);
        assertThat(politica.permite(f)).isEqualTo(f.planoMinimo() == Plano.FREE);
    }

    @Test
    void premiumAtivoPermiteRecursosPremiumNaoSensiveis() {
        var politica = politica(new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA), SEM_CONSENTIMENTO);
        assertThat(politica.permite(Funcionalidade.RELATORIO_PDF)).isTrue();
        assertThat(politica.permite(Funcionalidade.HISTORICO_COMPLETO)).isTrue();
    }

    @Test
    void moduloSensivelExigeConsentimentoMesmoNoPremium() {
        var estado = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);
        assertThat(politica(estado, SEM_CONSENTIMENTO).permite(Funcionalidade.ARQUIVO_DE_MENSAGENS)).isFalse();
        assertThatThrownBy(() -> politica(estado, SEM_CONSENTIMENTO).exigir(Funcionalidade.ARQUIVO_DE_MENSAGENS))
                .isInstanceOfSatisfying(AcessoNegadoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(AcessoNegadoException.Motivo.CONSENTIMENTO_AUSENTE));
        assertThat(politica(estado, COM_CONSENTIMENTO).permite(Funcionalidade.ARQUIVO_DE_MENSAGENS)).isTrue();
    }

    @Test
    void toleranciaOfflineMantemPremium() {
        var estado = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.TOLERANCIA_OFFLINE);
        assertThat(politica(estado, SEM_CONSENTIMENTO).planoEfetivo()).isEqualTo(Plano.PREMIUM);
    }

    @ParameterizedTest
    @EnumSource(value = SituacaoDaLicenca.class, names = {"AUSENTE", "EXPIRADA", "REVOGADA", "INVALIDA"})
    void licencaSemSituacaoVigenteNaoConcedePremiumMesmoDeclarandoPlanoPremium(SituacaoDaLicenca situacao) {
        var estado = new EstadoDaLicenca(Plano.PREMIUM, situacao);
        var politica = politica(estado, COM_CONSENTIMENTO);
        assertThat(politica.planoEfetivo()).isEqualTo(Plano.FREE);
        assertThatThrownBy(() -> politica.exigir(Funcionalidade.RELATORIO_PDF))
                .isInstanceOfSatisfying(AcessoNegadoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(AcessoNegadoException.Motivo.PLANO_INSUFICIENTE));
    }

    @Test
    void recursosFreeSaoSempreAcessiveisEmQualquerSituacao() {
        for (SituacaoDaLicenca situacao : SituacaoDaLicenca.values()) {
            var politica = politica(new EstadoDaLicenca(Plano.PREMIUM, situacao), SEM_CONSENTIMENTO);
            assertThat(politica.permite(Funcionalidade.EXCLUSAO_DE_DADOS)).isTrue();
            assertThat(politica.permite(Funcionalidade.COMPARACAO_MANUAL_LISTAS)).isTrue();
        }
    }

    @Test
    void identificadoresSaoUnicosEEstaveis() {
        Set<String> ids = new HashSet<>();
        for (Funcionalidade f : EnumSet.allOf(Funcionalidade.class)) {
            assertThat(ids.add(f.id())).as("id duplicado %s", f.id()).isTrue();
            assertThat(f.id()).matches("[a-z]+(\\.[a-z]+)+|[a-z]+");
        }
        assertThat(Funcionalidade.RELATORIO_PDF.id()).isEqualTo("relatorio.pdf");
        assertThat(Funcionalidade.ARQUIVO_DE_MENSAGENS.id()).isEqualTo("mensagens.arquivo");
    }

    @Test
    void mensagemDeNegacaoNaoExpoeDetalhesInternos() {
        var e = new AcessoNegadoException(Funcionalidade.RELATORIO_PDF, AcessoNegadoException.Motivo.PLANO_INSUFICIENTE);
        assertThat(e.getMessage()).doesNotContain("RELATORIO_PDF", "class", "Exception");
    }
}
