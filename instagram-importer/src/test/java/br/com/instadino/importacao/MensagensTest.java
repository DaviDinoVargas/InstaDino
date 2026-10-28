package br.com.instadino.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.fixtures.ConstrutorDeExportacao;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MensagensTest {

    private static final long JAN = 1_704_110_400_000L; // 2024-01-01 12:00 UTC em ms
    private static final long MES = 31L * 86_400_000L;
    private static final String SEGREDO = "senha-do-cofre-123";

    @TempDir
    Path pasta;

    private static String mensagem(String remetente, long ms, String extra) {
        return "{\"sender_name\":\"" + remetente + "\",\"timestamp_ms\":" + ms + ",\"content\":\"" + SEGREDO + "\"" + extra + "}";
    }

    private static String conversa(String titulo, String[] participantes, String... mensagens) {
        StringBuilder p = new StringBuilder();
        for (String nome : participantes) {
            p.append(p.length() > 0 ? "," : "").append("{\"name\":\"").append(nome).append("\"}");
        }
        return "{\"participants\":[" + p + "],\"messages\":[" + String.join(",", mensagens) + "],\"title\":\"" + titulo
                + "\",\"is_still_participant\":true,\"thread_path\":\"inbox/x_1\"}";
    }

    private ConstrutorDeExportacao base() {
        return new ConstrutorDeExportacao().seguidores("ana").seguindo("ana");
    }

    private ResultadoDaImportacao importar(ConstrutorDeExportacao c, boolean autorizar) {
        Path zip = c.escreverEm(pasta.resolve("e.zip"));
        var config = new ConfiguracaoDeImportacao(autorizar ? Set.of(CategoriaDeDados.MENSAGENS) : Set.of());
        return new ImportadorDeExportacao(LimitesDeImportacao.padrao()).importar(zip, config, new Cancelamento(), OuvinteDeProgresso.NENHUM);
    }

    private ConstrutorDeExportacao duasConversas() {
        return base()
                .arquivo("conta/your_instagram_activity/messages/inbox/joao_111/message_1.json", conversa("Jo\\u00e3o", new String[] {"Eu Mesmo", "Joao"},
                        mensagem("Eu Mesmo", JAN, ""), mensagem("Joao", JAN + 1000, ",\"photos\":[{\"uri\":\"a.jpg\"}]"), mensagem("Joao", JAN + MES, "")))
                .arquivo("conta/your_instagram_activity/messages/inbox/grupo_222/message_1.json", conversa("Amigos", new String[] {"Eu Mesmo", "Ana", "Bia"},
                        mensagem("Ana", JAN, ""), mensagem("Eu Mesmo", JAN + 5000, ",\"videos\":[{\"uri\":\"v.mp4\"}]")));
    }

    @Test
    void agregaPorConversaEMesEIdentificaOTitular() {
        ResultadoDaImportacao r = importar(duasConversas(), true);
        assertThat(r.conversas()).hasSize(2);
        ResumoDeConversa joao = r.conversas().get(0);
        assertThat(joao.titulo()).isEqualTo("João");
        assertThat(joao.total()).isEqualTo(3);
        assertThat(joao.enviadas()).isEqualTo(1);
        assertThat(joao.midias()).isEqualTo(1);
        assertThat(joao.grupo()).isFalse();
        assertThat(joao.meses()).extracting(ResumoDeConversa.Mes::mes).containsExactly("2024-01", "2024-02");
        assertThat(joao.meses()).extracting(ResumoDeConversa.Mes::total).containsExactly(2, 1);
        assertThat(joao.primeiroInstante()).isEqualTo(JAN / 1000);
        ResumoDeConversa grupo = r.conversas().get(1);
        assertThat(grupo.grupo()).isTrue();
        assertThat(grupo.participantes()).isEqualTo(3);
        assertThat(grupo.enviadas()).isEqualTo(1);
        assertThat(grupo.midias()).isEqualTo(1);
        assertThat(r.categoriasProcessadas()).contains(CategoriaDeDados.MENSAGENS);
    }

    @Test
    void semAutorizacaoAsMensagensNaoSaoLidas() {
        ResultadoDaImportacao r = importar(duasConversas(), false);
        assertThat(r.conversas()).isEmpty();
        assertThat(r.categoriasProcessadas()).doesNotContain(CategoriaDeDados.MENSAGENS);
        assertThat(r.avisos()).anyMatch(a -> a.contains("Mensagens") && a.contains("não foi autorizada"));
    }

    @Test
    void oTextoDasMensagensNuncaApareceNoResultadoNemNosDiagnosticos() {
        ResultadoDaImportacao r = importar(duasConversas(), true);
        String tudo = r.conversas().toString() + r.diagnosticos() + r.avisos() + r.atividades();
        assertThat(tudo).doesNotContain(SEGREDO).doesNotContain("a.jpg").doesNotContain("v.mp4");
    }

    @Test
    void diagnosticoDeMensagensNaoRevelaNomesDeUsuarioDasPastas() {
        ResultadoDaImportacao r = importar(duasConversas(), true);
        var diagnosticos = r.diagnosticos().stream().filter(d -> d.categoria() == CategoriaDeDados.MENSAGENS).toList();
        assertThat(diagnosticos).hasSize(1);
        assertThat(diagnosticos.get(0).caminho()).doesNotContain("joao").doesNotContain("grupo_222").contains("2 arquivos");
        assertThat(diagnosticos.get(0).elementosLidos()).isEqualTo(5);
    }

    @Test
    void umaUnicaConversaNaoPermiteSaberQuemEOTitular() {
        var c = base().arquivo("conta/messages/inbox/joao_111/message_1.json", conversa("Joao", new String[] {"Eu", "Joao"},
                mensagem("Eu", JAN, ""), mensagem("Joao", JAN + 1, "")));
        ResultadoDaImportacao r = importar(c, true);
        assertThat(r.conversas()).hasSize(1);
        assertThat(r.conversas().get(0).titularIdentificado()).isFalse();
        assertThat(r.conversas().get(0).enviadas()).isEqualTo(-1);
        assertThat(r.avisos()).anyMatch(a -> a.contains("identificar qual participante é você"));
    }

    @Test
    void juntaVariosArquivosDaMesmaConversa() {
        var c = base()
                .arquivo("conta/messages/inbox/joao_111/message_1.json", conversa("Joao", new String[] {"Eu", "Joao"}, mensagem("Eu", JAN, "")))
                .arquivo("conta/messages/inbox/joao_111/message_2.json", conversa("Joao", new String[] {"Eu", "Joao"}, mensagem("Joao", JAN + 1, "")))
                .arquivo("conta/messages/inbox/maria_333/message_1.json", conversa("Maria", new String[] {"Eu", "Maria"}, mensagem("Eu", JAN, "")));
        ResultadoDaImportacao r = importar(c, true);
        assertThat(r.conversas()).hasSize(2);
        assertThat(r.conversas().get(0).total()).isEqualTo(2);
        assertThat(r.conversas().get(0).titulo()).isEqualTo("Joao");
    }

    @Test
    void ignoraArquivosQueNaoSaoMensagensEMensagensSemDataOuRemetente() {
        var c = duasConversas()
                .arquivo("conta/messages/inbox/joao_111/outro.json", "{\"qualquer\":1}")
                .arquivo("conta/messages/inbox/ruim_444/message_1.json", conversa("Ruim", new String[] {"Eu", "Ruim"},
                        "{\"sender_name\":\"Eu\"}", "{\"timestamp_ms\":5}", mensagem("Eu", JAN, "")));
        ResultadoDaImportacao r = importar(c, true);
        assertThat(r.conversas()).hasSize(3);
        assertThat(r.conversas().stream().filter(x -> x.titulo().equals("Ruim")).findFirst().orElseThrow().total()).isEqualTo(1);
        assertThat(r.invalidosPorMotivo().values().stream().mapToInt(Integer::intValue).sum() + r.registrosInvalidos()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void arquivoDeMensagensCorrompidoViraAvisoSemDerrubarAImportacao() {
        var c = duasConversas().arquivo("conta/messages/inbox/quebrado_555/message_1.json", "{\"participants\":[{\"name\":\"x\"}],\"messages\":[{\"sender_name\"");
        ResultadoDaImportacao r = importar(c, true);
        assertThat(r.conversas()).hasSize(2);
        assertThat(r.categoriasIncompletas()).contains(CategoriaDeDados.MENSAGENS);
    }
}
