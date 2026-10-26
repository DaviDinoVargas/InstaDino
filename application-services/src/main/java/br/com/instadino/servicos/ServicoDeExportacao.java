package br.com.instadino.servicos;

import br.com.instadino.analise.exportacao.ExportadorCsv;
import br.com.instadino.analise.social.AnaliseDeSeguidores;
import br.com.instadino.nucleo.acesso.Funcionalidade;
import br.com.instadino.nucleo.acesso.PoliticaDeAcesso;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Exportações em CSV. O CSV básico é do plano Free; a escolha de colunas e filtros é Premium. */
public final class ServicoDeExportacao {

    public enum Coluna {
        USUARIO("usuario"), PERFIL("perfil"), DATA_REGISTRO("data_registro_utc");

        private final String titulo;

        Coluna(String titulo) {
            this.titulo = titulo;
        }

        public String titulo() {
            return titulo;
        }
    }

    private final PoliticaDeAcesso politica;
    private final ServicoDeRelacionamentos relacionamentos;
    private final RegistroDeAuditoriaLocal auditoria;

    ServicoDeExportacao(PoliticaDeAcesso politica, ServicoDeRelacionamentos relacionamentos, RegistroDeAuditoriaLocal auditoria) {
        this.politica = politica;
        this.relacionamentos = relacionamentos;
        this.auditoria = auditoria;
    }

    public void exportarCsv(VisaoDeRelacionamento visao, OutputStream saida) throws IOException {
        politica.exigir(Funcionalidade.EXPORTACAO_CSV_BASICA);
        escrever(relacionamentos.todas(visao), visao, EnumSet.allOf(Coluna.class), saida);
        auditoria.registrar("CSV_EXPORTADO", visao.name());
    }

    /** Premium: colunas escolhidas e consulta com busca, ordem e filtros compostos. */
    public void exportarCsvPersonalizado(ConsultaDeRelacionamentos consulta, Set<Coluna> colunas, OutputStream saida) throws IOException {
        politica.exigir(Funcionalidade.CSV_PERSONALIZADO);
        if (colunas.isEmpty()) {
            throw new IllegalArgumentException("Escolha ao menos uma coluna.");
        }
        List<ContaListada> contas = new ArrayList<>();
        int pagina = 0;
        Pagina<ContaListada> atual;
        do {
            atual = relacionamentos.consultar(consulta.naPagina(pagina++));
            contas.addAll(atual.itens());
        } while (pagina < atual.totalDePaginas());
        escrever(contas, consulta.visao(), colunas, saida);
        auditoria.registrar("CSV_EXPORTADO", consulta.visao().name());
    }

    private static void escrever(List<ContaListada> contas, VisaoDeRelacionamento visao, Set<Coluna> colunas, OutputStream saida)
            throws IOException {
        List<Coluna> ordem = EnumSet.copyOf(colunas).stream().toList();
        List<String> cabecalho = ordem.stream().map(Coluna::titulo).toList();
        List<List<?>> linhas = new ArrayList<>();
        for (ContaListada c : contas) {
            List<Object> linha = new ArrayList<>();
            for (Coluna col : ordem) {
                linha.add(switch (col) {
                    case USUARIO -> c.usuario();
                    case PERFIL -> c.urlDoPerfil();
                    case DATA_REGISTRO -> c.registradaEm() == null ? "" : c.registradaEm().toString();
                });
            }
            linhas.add(linha);
        }
        if (visao == VisaoDeRelacionamento.NAO_SEGUEM_DE_VOLTA || visao == VisaoDeRelacionamento.VOCE_NAO_SEGUE) {
            List<Object> vazia = new ArrayList<>();
            List<Object> observacao = new ArrayList<>();
            for (int i = 0; i < ordem.size(); i++) {
                vazia.add("");
                observacao.add(i == 0 ? "Observação" : i == 1 ? AnaliseDeSeguidores.LIMITACAO : "");
            }
            linhas.add(vazia);
            linhas.add(observacao);
        }
        ExportadorCsv.escrever(saida, cabecalho, linhas);
    }
}
