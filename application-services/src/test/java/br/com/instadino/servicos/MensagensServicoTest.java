package br.com.instadino.servicos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import br.com.instadino.importacao.Cancelamento;
import br.com.instadino.importacao.CategoriaDeDados;
import br.com.instadino.importacao.ConfiguracaoDeImportacao;
import br.com.instadino.importacao.OuvinteDeProgresso;
import br.com.instadino.nucleo.acesso.AcessoNegadoException;
import br.com.instadino.nucleo.acesso.EstadoDaLicenca;
import br.com.instadino.nucleo.acesso.Plano;
import br.com.instadino.nucleo.acesso.SituacaoDaLicenca;
import br.com.instadino.persistencia.seguranca.ProtetorSomenteDesenvolvimento;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MensagensServicoTest {

    private static final EstadoDaLicenca PREMIUM = new EstadoDaLicenca(Plano.PREMIUM, SituacaoDaLicenca.ATIVA);
    private static final EstadoDaLicenca FREE = new EstadoDaLicenca(Plano.FREE, SituacaoDaLicenca.AUSENTE);
    private static final long JAN = 1_704_110_400_000L;
    private static final long MES = 31L * 86_400_000L;

    @TempDir
    Path pasta;

    private final AtomicReference<EstadoDaLicenca> licenca = new AtomicReference<>(PREMIUM);
    private ContextoLocal ctx;

    @BeforeEach
    void abrir() {
        ctx = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca::get);
    }

    @AfterEach
    void fechar() {
        ctx.close();
    }

    private static String m(String quem, long ms) {
        return "{\"sender_name\":\"" + quem + "\",\"timestamp_ms\":" + ms + ",\"content\":\"conteudo\"}";
    }

    private static String conversa(String titulo, String outro, String... mensagens) {
        return "{\"participants\":[{\"name\":\"Eu\"},{\"name\":\"" + outro + "\"}],\"messages\":[" + String.join(",", mensagens)
                + "],\"title\":\"" + titulo + "\"}";
    }

    private void importar() {
        var c = new ConstrutorDeExportacao().seguidores("ana").seguindo("ana")
                .arquivo("conta/messages/inbox/a_1/message_1.json", conversa("Ana", "Ana", m("Eu", JAN), m("Ana", JAN + 1), m("Ana", JAN + MES)))
                .arquivo("conta/messages/inbox/b_2/message_1.json", conversa("Bia", "Bia", m("Bia", JAN)));
        Path zip = c.escreverEm(pasta.resolve("e-" + System.nanoTime() + ".zip"));
        var r = ctx.importacao().processar(zip, new ConfiguracaoDeImportacao(Set.of(CategoriaDeDados.MENSAGENS)), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        ctx.importacao().confirmar(r, ctx.sessao().perfilAtivo(), "e.zip", 1L);
    }

    @Test
    void semConsentimentoAsMensagensNaoSaoImportadasNemLidas() {
        importar();
        assertThat(ctx.mensagens().consentimentoConcedido()).isFalse();
        assertThat(ctx.mensagens().disponivel()).isFalse();
        assertThat(ctx.mensagens().temDados()).isFalse();
        assertThatThrownBy(() -> ctx.mensagens().resumo()).isInstanceOf(AcessoNegadoException.class)
                .extracting(e -> ((AcessoNegadoException) e).motivo()).isEqualTo(AcessoNegadoException.Motivo.CONSENTIMENTO_AUSENTE);
        ctx.mensagens().conceder(); // depois de conceder, nada foi guardado antes
        assertThat(ctx.mensagens().resumo()).isEmpty();
    }

    @Test
    void comConsentimentoOResumoOHistoricoPorMesEORankingFuncionam() {
        ctx.mensagens().conceder();
        importar();
        var resumo = ctx.mensagens().resumo().orElseThrow();
        assertThat(resumo.conversas()).isEqualTo(2);
        assertThat(resumo.mensagens()).isEqualTo(4);
        assertThat(resumo.enviadas()).isEqualTo(1);
        assertThat(resumo.recebidas()).isEqualTo(3);
        assertThat(resumo.mesesComMensagens()).isEqualTo(2);
        var ranking = ctx.mensagens().ranking(10, false);
        assertThat(ranking).extracting(ServicoDeMensagens.Contato::titulo).containsExactly("Ana", "Bia");
        assertThat(ranking.get(0).enviadas()).isEqualTo(1);
        assertThat(ctx.mensagens().porMes()).extracting(ServicoDeMensagens.Mes::mes).containsExactly(YearMonth.of(2024, 1), YearMonth.of(2024, 2));
        assertThat(ctx.mensagens().porMes().get(0).total()).isEqualTo(3);
    }

    @Test
    void revogarApagaOsDadosEFechaOAcesso() {
        ctx.mensagens().conceder();
        importar();
        assertThat(ctx.mensagens().temDados()).isTrue();
        assertThat(ctx.mensagens().revogarEApagar()).isEqualTo(2);
        assertThat(ctx.mensagens().consentimentoConcedido()).isFalse();
        assertThat(ctx.mensagens().temDados()).isFalse();
        assertThatThrownBy(() -> ctx.mensagens().ranking(5, true)).isInstanceOf(AcessoNegadoException.class);
        ctx.mensagens().conceder();
        assertThat(ctx.mensagens().resumo()).isEmpty();
    }

    @Test
    void planoFreeNaoPodeHabilitarOModulo() {
        licenca.set(FREE);
        assertThatThrownBy(() -> ctx.mensagens().conceder()).isInstanceOf(AcessoNegadoException.class)
                .extracting(e -> ((AcessoNegadoException) e).motivo()).isEqualTo(AcessoNegadoException.Motivo.PLANO_INSUFICIENTE);
        assertThat(ctx.mensagens().consentimentoConcedido()).isFalse();
    }

    @Test
    void depoisDeVoltarAoFreeOsDadosFicamIlegiveisMasPodemSerApagados() {
        ctx.mensagens().conceder();
        importar();
        licenca.set(FREE);
        assertThat(ctx.mensagens().disponivel()).isFalse();
        assertThatThrownBy(() -> ctx.mensagens().resumo()).isInstanceOf(AcessoNegadoException.class);
        assertThat(ctx.mensagens().revogarEApagar()).isEqualTo(2);
        licenca.set(PREMIUM);
        ctx.mensagens().conceder();
        assertThat(ctx.mensagens().resumo()).isEmpty();
    }

    @Test
    void importacaoFreeIgnoraMensagensMesmoSolicitadas() {
        ctx.mensagens().conceder();
        licenca.set(FREE);
        importar();
        licenca.set(PREMIUM);
        assertThat(ctx.mensagens().resumo()).isEmpty();
    }

    @Test
    void excluirTodosOsDadosApagaAsConversas() {
        ctx.mensagens().conceder();
        importar();
        ctx.dados().excluirTodosOsDados();
        ctx = ContextoLocal.abrir(pasta.resolve("dados"), new ProtetorSomenteDesenvolvimento(), licenca::get);
        assertThat(ctx.mensagens().consentimentoConcedido()).isFalse();
        ctx.mensagens().conceder();
        assertThat(ctx.mensagens().temDados()).isFalse();
    }

    @Test
    void nenhumTextoDeMensagemOuNomeDeMidiaChegaAoBancoEmTabelaNenhuma() {
        ctx.mensagens().conceder();
        var c = new ConstrutorDeExportacao().seguidores("ana").seguindo("ana")
                .arquivo("conta/messages/inbox/a_1/message_1.json", "{\"participants\":[{\"name\":\"Eu\"},{\"name\":\"Ana\"}],\"messages\":["
                        + "{\"sender_name\":\"Eu\",\"timestamp_ms\":" + JAN + ",\"content\":\"SEGREDO-DA-MENSAGEM-777\",\"photos\":[{\"uri\":\"FOTO-PRIVADA-888.jpg\"}],"
                        + "\"reactions\":[{\"reaction\":\"REACAO-999\",\"actor\":\"Ana\"}],\"share\":{\"link\":\"https://exemplo.test/LINK-SECRETO-555\"}},"
                        + "{\"sender_name\":\"Ana\",\"timestamp_ms\":" + (JAN + 1) + ",\"content\":\"OUTRO-SEGREDO-666\"}],\"title\":\"Ana\"}")
                .arquivo("conta/messages/inbox/b_2/message_1.json", conversa("Bia", "Bia", m("Bia", JAN)));
        Path zip = c.escreverEm(pasta.resolve("seg.zip"));
        var r = ctx.importacao().processar(zip, new ConfiguracaoDeImportacao(Set.of(CategoriaDeDados.MENSAGENS)), new Cancelamento(), OuvinteDeProgresso.NENHUM);
        ctx.importacao().confirmar(r, ctx.sessao().perfilAtivo(), "seg.zip", 1L);

        StringBuilder tudo = new StringBuilder();
        ctx.banco().emTransacao(conexao -> {
            try (var tabelas = conexao.createStatement().executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
                java.util.List<String> nomes = new java.util.ArrayList<>();
                while (tabelas.next()) {
                    nomes.add(tabelas.getString(1));
                }
                for (String nome : nomes) {
                    try (var linhas = conexao.createStatement().executeQuery("SELECT * FROM \"" + nome + "\"")) {
                        int colunas = linhas.getMetaData().getColumnCount();
                        while (linhas.next()) {
                            for (int k = 1; k <= colunas; k++) {
                                tudo.append(linhas.getString(k)).append('|');
                            }
                        }
                    }
                }
            }
            return null;
        });
        assertThat(tudo.toString()).contains("Ana").contains("Bia"); // os agregados estão lá
        assertThat(tudo.toString()).doesNotContain("SEGREDO-DA-MENSAGEM-777").doesNotContain("OUTRO-SEGREDO-666").doesNotContain("FOTO-PRIVADA-888")
                .doesNotContain("REACAO-999").doesNotContain("LINK-SECRETO-555").doesNotContain("exemplo.test");
    }
}
