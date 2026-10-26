package br.com.instadino.persistencia.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.nucleo.dominio.NomeDeUsuario;
import br.com.instadino.nucleo.dominio.Relacionamento;
import br.com.instadino.nucleo.dominio.TipoDeRelacionamento;
import br.com.instadino.persistencia.BancoException;
import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.Chaves;
import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
import br.com.instadino.persistencia.repositorio.RepositorioDeImportacoes.NovaImportacao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositorioDeImportacoesTest {

    @TempDir
    Path pasta;

    BancoLocal banco;
    RepositorioDePerfis perfis;
    RepositorioDeImportacoes importacoes;
    long perfilId;

    @BeforeEach
    void preparar() {
        banco = BancoLocal.abrir(pasta.resolve("i.db"), Chaves.A, CarregadorDeMigracoes.doClasspath());
        perfis = new RepositorioDePerfis(banco);
        importacoes = new RepositorioDeImportacoes(banco);
        perfilId = perfis.obterOuCriarPadrao().id();
    }

    @AfterEach
    void encerrar() {
        banco.close();
    }

    private static List<Relacionamento> lista(TipoDeRelacionamento tipo, String... nomes) {
        List<Relacionamento> r = new ArrayList<>();
        for (int i = 0; i < nomes.length; i++) {
            r.add(new Relacionamento(tipo, new NomeDeUsuario(nomes[i]), i % 2 == 0 ? 1700000000L + i : null));
        }
        return r;
    }

    private NovaImportacao nova(List<Relacionamento> rels) {
        Map<String, int[]> categorias = new LinkedHashMap<>();
        categorias.put("SEGUIDORES", new int[] {1, rels.size()});
        return new NovaImportacao(perfilId, "1.0.0", "ZIP oficial", "export.zip", 1234L, rels.size(), 2, categorias, rels, null);
    }

    @Test
    void perfilPadraoECriadoUmaUnicaVez() {
        assertThat(perfis.obterOuCriarPadrao().id()).isEqualTo(perfilId);
        assertThat(perfis.listar()).hasSize(1);
    }

    @Test
    void salvaERecuperaImportacaoComRelacionamentos() {
        var rels = new ArrayList<>(lista(TipoDeRelacionamento.SEGUIDOR, "ana", "bruno", "carla"));
        rels.addAll(lista(TipoDeRelacionamento.SEGUINDO, "bruno", "dora"));
        String id = importacoes.salvar(nova(rels), 0);

        var resumo = importacoes.obter(id).orElseThrow();
        assertThat(resumo.versaoDoParser()).isEqualTo("1.0.0");
        assertThat(resumo.origem()).isEqualTo("ZIP oficial");
        assertThat(resumo.nomeDoArquivo()).isEqualTo("export.zip");
        assertThat(resumo.tamanhoEmBytes()).isEqualTo(1234L);
        assertThat(resumo.registrosInvalidos()).isEqualTo(2);
        assertThat(resumo.categorias().get("SEGUIDORES")).containsExactly(1, 5);

        assertThat(importacoes.relacionamentos(id, TipoDeRelacionamento.SEGUIDOR)).extracting(r -> r.usuario().valor())
                .containsExactly("ana", "bruno", "carla");
        assertThat(importacoes.relacionamentos(id, TipoDeRelacionamento.SEGUINDO)).hasSize(2);
        assertThat(importacoes.relacionamentos(id, TipoDeRelacionamento.SEGUIDOR).get(0).instanteEmSegundos()).isEqualTo(1700000000L);
        assertThat(importacoes.relacionamentos(id, TipoDeRelacionamento.SEGUIDOR).get(1).instanteEmSegundos()).isNull();
        assertThat(importacoes.contar(id, TipoDeRelacionamento.SEGUIDOR)).isEqualTo(3);
    }

    @Test
    void historicoVemDaMaisRecenteParaAMaisAntigaEPersisteAposReabrir() {
        String a = importacoes.salvar(nova(lista(TipoDeRelacionamento.SEGUIDOR, "a")), 0);
        String b = importacoes.salvar(nova(lista(TipoDeRelacionamento.SEGUIDOR, "b")), 0);
        banco.close();
        banco = BancoLocal.abrir(pasta.resolve("i.db"), Chaves.A, CarregadorDeMigracoes.doClasspath());
        importacoes = new RepositorioDeImportacoes(banco);
        assertThat(importacoes.listar(perfilId)).extracting(RepositorioDeImportacoes.ResumoDaImportacao::id).containsExactly(b, a);
        assertThat(importacoes.maisRecente(perfilId).orElseThrow().id()).isEqualTo(b);
    }

    @Test
    void limiteDeRetencaoManteApenasAsMaisRecentesNaMesmaTransacao() {
        importacoes.salvar(nova(lista(TipoDeRelacionamento.SEGUIDOR, "a")), 1);
        importacoes.salvar(nova(lista(TipoDeRelacionamento.SEGUIDOR, "b")), 1);
        String c = importacoes.salvar(nova(lista(TipoDeRelacionamento.SEGUIDOR, "c")), 1);
        assertThat(importacoes.listar(perfilId)).extracting(RepositorioDeImportacoes.ResumoDaImportacao::id).containsExactly(c);
        int orfaos = banco.emTransacao(conn -> {
            try (var s = conn.createStatement(); var r = s.executeQuery(
                    "SELECT count(*) FROM relacionamento WHERE importacao_id NOT IN (SELECT id FROM importacao)")) {
                r.next();
                return r.getInt(1);
            }
        });
        assertThat(orfaos).isZero();
    }

    @Test
    void falhaNaGravacaoNaoAlteraOHistoricoAnterior() {
        String anterior = importacoes.salvar(nova(lista(TipoDeRelacionamento.SEGUIDOR, "ana", "bruno")), 1);
        // chave primária duplicada (mesmo usuário duas vezes) força erro no meio da gravação
        var duplicado = new ArrayList<>(lista(TipoDeRelacionamento.SEGUIDOR, "x", "y"));
        duplicado.add(new Relacionamento(TipoDeRelacionamento.SEGUIDOR, new NomeDeUsuario("x"), null));
        assertThatThrownBy(() -> importacoes.salvar(nova(duplicado), 1)).isInstanceOf(BancoException.class);

        assertThat(importacoes.listar(perfilId)).extracting(RepositorioDeImportacoes.ResumoDaImportacao::id).containsExactly(anterior);
        assertThat(importacoes.relacionamentos(anterior, TipoDeRelacionamento.SEGUIDOR)).hasSize(2);
    }

    @Test
    void perfilInexistenteERejeitadoPelaChaveEstrangeira() {
        var invalida = new NovaImportacao(9999, "1", "zip", null, null, 0, 0, Map.of(), List.of(), null);
        assertThatThrownBy(() -> importacoes.salvar(invalida, 0)).isInstanceOf(BancoException.class);
    }

    @Test
    void exclusaoRemoveRelacionamentosECategoriasEmCascata() {
        String id = importacoes.salvar(nova(lista(TipoDeRelacionamento.SEGUIDOR, "a", "b")), 0);
        importacoes.excluir(id);
        assertThat(importacoes.obter(id)).isEmpty();
        assertThat(importacoes.contar(id, TipoDeRelacionamento.SEGUIDOR)).isZero();
    }

    @Test
    void excluirTodasEExcluirPerfilLimpamOsDados() {
        importacoes.salvar(nova(lista(TipoDeRelacionamento.SEGUIDOR, "a")), 0);
        importacoes.salvar(nova(lista(TipoDeRelacionamento.SEGUIDOR, "b")), 0);
        importacoes.excluirTodas(perfilId);
        assertThat(importacoes.listar(perfilId)).isEmpty();
        importacoes.salvar(nova(lista(TipoDeRelacionamento.SEGUIDOR, "c")), 0);
        perfis.excluir(perfilId);
        assertThat(importacoes.listar(perfilId)).isEmpty();
    }

    @Test
    void perfilComNomeInvalidoERejeitado() {
        assertThatThrownBy(() -> perfis.criar("   ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> perfis.criar("x".repeat(101), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gravaCentenasDeMilharesDeRegistrosEmTempoRazoavel() {
        var rels = new ArrayList<Relacionamento>();
        for (int i = 0; i < 150_000; i++) {
            rels.add(new Relacionamento(TipoDeRelacionamento.SEGUIDOR, new NomeDeUsuario("usuario" + i), 1700000000L));
        }
        long inicio = System.nanoTime();
        String id = importacoes.salvar(nova(rels), 0);
        long segundos = (System.nanoTime() - inicio) / 1_000_000_000L;
        assertThat(importacoes.contar(id, TipoDeRelacionamento.SEGUIDOR)).isEqualTo(150_000);
        assertThat(segundos).as("tempo de gravação de 150 mil registros").isLessThan(60);
    }
}
