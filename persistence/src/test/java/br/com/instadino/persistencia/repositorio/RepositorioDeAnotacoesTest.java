package br.com.instadino.persistencia.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.instadino.persistencia.BancoLocal;
import br.com.instadino.persistencia.Chaves;
import br.com.instadino.persistencia.migracao.CarregadorDeMigracoes;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositorioDeAnotacoesTest {

    @TempDir
    Path pasta;

    private BancoLocal banco;
    private RepositorioDeAnotacoes anotacoes;
    private long perfilA;
    private long perfilB;

    @BeforeEach
    void preparar() {
        banco = BancoLocal.abrir(pasta.resolve("a.db"), Chaves.A, CarregadorDeMigracoes.doClasspath());
        RepositorioDePerfis perfis = new RepositorioDePerfis(banco);
        perfilA = perfis.criar("A", null).id();
        perfilB = perfis.criar("B", null).id();
        anotacoes = new RepositorioDeAnotacoes(banco);
    }

    @AfterEach
    void fechar() {
        banco.close();
    }

    @Test
    void favoritoENotaSaoGravadosNormalizadosEPersistemAposReabrir() {
        anotacoes.definirFavorito(perfilA, "@Ana.Silva", true);
        anotacoes.definirNota(perfilA, "ana.silva", "  conheci no evento  ");
        banco.close();
        banco = BancoLocal.abrir(pasta.resolve("a.db"), Chaves.A, CarregadorDeMigracoes.doClasspath());
        anotacoes = new RepositorioDeAnotacoes(banco);
        var a = anotacoes.obter(perfilA, "ANA.SILVA").orElseThrow();
        assertThat(a.favorito()).isTrue();
        assertThat(a.nota()).isEqualTo("conheci no evento");
    }

    @Test
    void desfazerTudoRemoveARegistroVazia() {
        anotacoes.definirFavorito(perfilA, "ana", true);
        anotacoes.definirNota(perfilA, "ana", "x");
        anotacoes.definirFavorito(perfilA, "ana", false);
        assertThat(anotacoes.doPerfil(perfilA)).containsKey("ana");
        anotacoes.definirNota(perfilA, "ana", "   ");
        assertThat(anotacoes.doPerfil(perfilA)).isEmpty();
    }

    @Test
    void etiquetasSaoCriadasAplicadasRenomeadasEExcluidas() {
        var amigo = anotacoes.criarEtiqueta(perfilA, "  Amigo   próximo ");
        assertThat(amigo.nome()).isEqualTo("Amigo próximo");
        anotacoes.aplicarEtiqueta(perfilA, "ana", amigo.id());
        anotacoes.aplicarEtiqueta(perfilA, "ana", amigo.id()); // repetir é inofensivo
        assertThat(anotacoes.obter(perfilA, "ana").orElseThrow().etiquetas()).extracting(RepositorioDeAnotacoes.Etiqueta::nome).containsExactly("Amigo próximo");
        anotacoes.renomearEtiqueta(perfilA, amigo.id(), "Trabalho");
        assertThat(anotacoes.obter(perfilA, "ana").orElseThrow().etiquetas()).extracting(RepositorioDeAnotacoes.Etiqueta::nome).containsExactly("Trabalho");
        anotacoes.removerEtiqueta(perfilA, "ana", amigo.id());
        assertThat(anotacoes.doPerfil(perfilA)).isEmpty();
        anotacoes.aplicarEtiqueta(perfilA, "bia", amigo.id());
        anotacoes.excluirEtiqueta(perfilA, amigo.id());
        assertThat(anotacoes.doPerfil(perfilA)).isEmpty();
        assertThat(anotacoes.etiquetas(perfilA)).isEmpty();
    }

    @Test
    void nomeDeEtiquetaDuplicadoSemDistinguirCaixaEhRecusado() {
        anotacoes.criarEtiqueta(perfilA, "Família");
        assertThatThrownBy(() -> anotacoes.criarEtiqueta(perfilA, "FAMÍLIA".toLowerCase())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anotacoes.criarEtiqueta(perfilA, "família")).isInstanceOf(IllegalArgumentException.class);
        anotacoes.criarEtiqueta(perfilB, "Família"); // outro perfil pode repetir o nome
    }

    @Test
    void perfisSaoIsolados() {
        var etiquetaB = anotacoes.criarEtiqueta(perfilB, "Só do B");
        anotacoes.definirFavorito(perfilA, "ana", true);
        assertThat(anotacoes.doPerfil(perfilB)).isEmpty();
        // IDOR local: não é possível aplicar a etiqueta de um perfil a contas de outro
        assertThatThrownBy(() -> anotacoes.aplicarEtiqueta(perfilA, "ana", etiquetaB.id())).isInstanceOf(IllegalArgumentException.class);
        assertThat(anotacoes.obter(perfilA, "ana").orElseThrow().etiquetas()).isEmpty();
        anotacoes.excluirEtiqueta(perfilA, etiquetaB.id()); // não pode excluir a etiqueta de outro perfil
        assertThat(anotacoes.etiquetas(perfilB)).hasSize(1);
        assertThatThrownBy(() -> anotacoes.renomearEtiqueta(perfilA, etiquetaB.id(), "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entradasInvalidasSaoRecusadasSemGravar() {
        assertThatThrownBy(() -> anotacoes.definirFavorito(perfilA, "nome inválido!", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anotacoes.definirNota(perfilA, "ana", "x".repeat(2001))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anotacoes.criarEtiqueta(perfilA, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anotacoes.criarEtiqueta(perfilA, "a".repeat(41))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anotacoes.criarEtiqueta(perfilA, "com\u0007controle")).isInstanceOf(IllegalArgumentException.class);
        assertThat(anotacoes.doPerfil(perfilA)).isEmpty();
        assertThat(anotacoes.etiquetas(perfilA)).isEmpty();
    }

    @Test
    void textoMaliciosoEGravadoComoTextoSemExecutarSql() {
        String sql = "x'); DROP TABLE anotacao_conta; --";
        anotacoes.definirNota(perfilA, "ana", sql);
        anotacoes.criarEtiqueta(perfilA, "'; DELETE FROM etiqueta; --");
        assertThat(anotacoes.obter(perfilA, "ana").orElseThrow().nota()).isEqualTo(sql);
        assertThat(anotacoes.etiquetas(perfilA)).hasSize(1);
    }

    @Test
    void limiteDeEtiquetasPorPerfil() {
        for (int i = 0; i < RepositorioDeAnotacoes.LIMITE_DE_ETIQUETAS_POR_PERFIL; i++) {
            anotacoes.criarEtiqueta(perfilA, "e" + i);
        }
        assertThatThrownBy(() -> anotacoes.criarEtiqueta(perfilA, "a mais")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Limite");
    }

    @Test
    void excluirOPerfilRemoveTudoEmCascata() {
        var e = anotacoes.criarEtiqueta(perfilB, "x");
        anotacoes.definirNota(perfilB, "ana", "n");
        anotacoes.aplicarEtiqueta(perfilB, "ana", e.id());
        new RepositorioDePerfis(banco).excluir(perfilB);
        assertThat(anotacoes.doPerfil(perfilB)).isEmpty();
        assertThat(anotacoes.etiquetas(perfilB)).isEmpty();
    }
}
