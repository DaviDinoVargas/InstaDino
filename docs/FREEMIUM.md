# Modelo Freemium — estado da implementação

A decisão de acesso é centralizada em `PoliticaDeAcesso` (módulo `application-core`) e **reaplicada nos casos de uso** (`application-services`); a interface apenas reflete. O plano Premium só é concedido por um `EstadoDaLicenca` validado criptograficamente (assinatura Ed25519 do servidor, **ainda não implementada**: ver `LICENSING.md` quando existir). Hoje, sem o cliente de licenciamento, o aplicativo usa o plano Free por padrão.

## Matriz (implementado e testado)

| Funcionalidade | Id | Plano | Aplicação |
|---|---|---|---|
| Importação do ZIP | `importacao.zip` | Free | `ServicoDeImportacao` |
| Painel social | `dashboard.social` | Free | `ServicoDeRelacionamentos.painel` |
| Listas (seguidores, seguindo, mútuos, não recíprocos) | `relacionamentos.basico` | Free | `consultar` |
| Busca e ordenação | `busca.basica` | Free | `consultar` |
| Comparação manual de listas | `comparacao.manual` | Free | `ServicoDeHistorico.compararManualmente` |
| CSV básico | `exportacao.csv.basica` | Free | `ServicoDeExportacao.exportarCsv` |
| Exclusão de dados | `dados.exclusao` | Free | `ServicoDeDados`, `ServicoDeHistorico.excluir` |
| Histórico completo | `historico.completo` | Premium | Free mantém só a última importação |
| Comparação entre períodos | `comparacao.periodos` | Premium | `ServicoDeHistorico.comparar` |
| Filtros compostos (período, só com data) | `filtros.compostos` | Premium | `consultar` |
| CSV personalizado | `exportacao.csv.personalizado` | Premium | `exportarCsvPersonalizado` |

Os demais itens do catálogo (`Funcionalidade`) existem para a política, mas **ainda não têm implementação**: gráficos históricos, relatório PDF, favoritos, etiquetas, notas, múltiplos perfis na interface, biblioteca de conteúdo, análise de atividade, explorador, painéis, backup protegido, relatórios comparativos e arquivo de mensagens.

## Regras verificadas por teste

- Free retém só a importação mais recente; Premium retém todas.
- Ao perder o Premium (licença expirada ou revogada) **nenhum dado local é apagado ou bloqueado**; o histórico volta a aparecer se a licença voltar.
- Um estado `PREMIUM` com situação expirada/revogada/inválida concede somente Free.
- Módulos sensíveis exigem plano Premium **e** consentimento gravado.
- Importações de um perfil não são acessíveis a partir de outro perfil ativo.
