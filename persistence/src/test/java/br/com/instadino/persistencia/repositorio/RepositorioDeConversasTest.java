package br.com.instadino.persistencia.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.Chaves;
import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.ConversaGravada;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.MesGravado;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.NovaImportacao;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositorioDeConversasTest {

    @TempDir
    Path pasta;

    private BancoLocal banco;
    private RepositorioDeImportacoes importacoes;
    private RepositorioDeConversas conversas;
    private long perfil;

    @BeforeEach
    void preparar() {
        banco = BancoLocal.abrir(pasta.resolve("c.db"), Chaves.A, CarregadorDeMigracoes.doClasspath());
        perfil = new RepositorioDePerfis(banco).criar("P", null).id();
        importacoes = new RepositorioDeImportacoes(banco);
        conversas = new RepositorioDeConversas(banco);
    }

    @AfterEach
    void fechar() {
        banco.close();
    }

    private String salvar(List<ConversaGravada> lista) {
        return importacoes.salvar(new NovaImportacao(perfil, "1.5.0", "teste", "e.zip", 1L, 0, 0, Map.of(), List.of(), null, List.of(), List.of(), lista), 0);
    }

    private static ConversaGravada c(String titulo, int participantes, int enviadas, Long primeiro, MesGravado... meses) {
        int total = 0;
        for (MesGravado m : meses) {
            total += m.total();
        }
        return new ConversaGravada(titulo, participantes, total, enviadas, 2, primeiro, primeiro == null ? null : primeiro + 100, List.of(meses));
    }

    @Test
    void gravaEConsultaAgregadosOrdenadosPorMovimento() {
        String id = salvar(List.of(
                c("Pouca", 2, 1, 1_700_000_000L, new MesGravado("2024-01", 2, 1)),
                c("Muita", 3, 4, 1_600_000_000L, new MesGravado("2024-01", 5, 3), new MesGravado("2024-02", 3, 1))));
        var lista = conversas.listar(id, 10);
        assertThat(lista).extracting(RepositorioDeConversas.Conversa::titulo).containsExactly("Muita", "Pouca");
        assertThat(lista.get(0).grupo()).isTrue();
        assertThat(lista.get(0).total()).isEqualTo(8);
        assertThat(lista.get(1).primeiro()).isEqualTo(1_700_000_000L);
        assertThat(conversas.contar(id)).isEqualTo(2);
        assertThat(conversas.porMes(id)).extracting(RepositorioDeConversas.Mes::mes, RepositorioDeConversas.Mes::total, RepositorioDeConversas.Mes::enviadas)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("2024-01", 7, 4), org.assertj.core.groups.Tuple.tuple("2024-02", 3, 1));
        assertThat(conversas.porMes(lista.get(0).id())).hasSize(2);
    }

    @Test
    void datasAusentesVoltamNulasENaoZero() {
        String id = salvar(List.of(c("Sem datas", 2, 0, null, new MesGravado("2024-01", 1, 0))));
        var c = conversas.listar(id, 10).get(0);
        assertThat(c.primeiro()).isNull();
        assertThat(c.ultimo()).isNull();
    }

    @Test
    void titularDesconhecidoPropagaParaOMesSemSomarMenosUm() {
        String id = salvar(List.of(c("A", 2, -1, 1L, new MesGravado("2024-01", 3, -1))));
        assertThat(conversas.listar(id, 10).get(0).titularIdentificado()).isFalse();
        assertThat(conversas.porMes(id).get(0).enviadas()).isEqualTo(-1);
    }

    @Test
    void excluirTodasApagaTudoEApagarAImportacaoApagaEmCascata() {
        String a = salvar(List.of(c("A", 2, 1, 1L, new MesGravado("2024-01", 3, 1))));
        String b = salvar(List.of(c("B", 2, 1, 1L, new MesGravado("2024-01", 3, 1))));
        importacoes.excluir(a);
        assertThat(conversas.contar(a)).isZero();
        assertThat(conversas.contar(b)).isEqualTo(1);
        assertThat(conversas.excluirTodas()).isEqualTo(1);
        assertThat(conversas.contar(b)).isZero();
        assertThat(conversas.porMes(b)).isEmpty();
    }

    @Test
    void limiteDeListagemEhRespeitado() {
        String id = salvar(List.of(c("A", 2, 1, 1L, new MesGravado("2024-01", 3, 1)), c("B", 2, 1, 1L, new MesGravado("2024-01", 2, 1))));
        assertThat(conversas.listar(id, 1)).hasSize(1);
        assertThat(conversas.listar(id, 0)).hasSize(1);
    }
}
