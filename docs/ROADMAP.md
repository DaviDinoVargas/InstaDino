# Roteiro de funcionalidades propostas

Origem: sugestões do responsável pelo produto após testar o aplicativo com uma exportação real (2026-10-26). Estado em 2026-10-28.

Legenda: **Feito** = implementado e testado com dados sintéticos; **Validado** = conferido com a exportação real do responsável; **Não feito** = fora desta versão, com o motivo.

| # | Proposta | Plano | Estado | Observações |
|---|---|---|---|---|
| 1 | Validar o importador com a exportação real | — | **Validado** (relacionamentos) | O responsável confirmou que a importação passou a bater. Atividade, conteúdo, anúncios e mensagens **não** foram conferidos com dados reais. |
| 2 | Não recíprocos com contexto | Premium | **Feito** | Aba Cruzamentos da tela Atividade: mostra contas não recíprocas com e sem interação registrada; não infere intenção. |
| 3 | Ranking de interação | Premium | **Feito** | Aba Ranking (curtidas, comentários, interações com stories). |
| 4 | Buscados e não seguidos | Premium | **Feito** | Cruzamento das pesquisas de perfil com as listas. |
| 5 | "Deixou de seguir" com origem declarada | Free | **Feito** | Lista do próprio Instagram (`recently_unfollowed_profiles`), coberta por aviso de que cobre só um período recente. |
| 6 | Linha do tempo e mapa de calor | Premium | **Feito** | Resumo por mês e mapa de calor por dia da semana e hora (fuso do computador). |
| 7 | "O que o Instagram sabe sobre mim" | Premium | **Parcial** | Feito: temas, anunciantes e anúncios (aba Anúncios e interesses), lidos de forma independente de rótulos. **Não feito:** histórico de logins e alertas de segurança, porque exigem tratar IPs e não afirmar além do que o arquivo prova. A decisão de plano mudou de Free para Premium por usar a mesma leitura de atividade, que o Free não lê. |
| 8 | Auditoria de contatos sincronizados | Premium, sensível | **Não feito** | Dados de terceiros (telefone/e-mail). Sem exportação real para validar o formato, optou-se por não construir um leitor às cegas para o dado de maior risco. |
| 9 | Estatísticas de mensagens | Premium + consentimento | **Feito** | Somente contagens e datas; consentimento explícito, desligado por padrão, revogável com apagamento. |
| 10 | Galeria local de mídia | Premium | **Não feito** | HEIC exige biblioteca de decodificação (nova dependência, a auditoria de dependências já está pendente) e o ZIP original precisaria continuar disponível. O catálogo de mídias (caminho, tipo, tamanho) existe na Biblioteca. |
| 11 | Ficha do perfil (busca global) | Free | **Não feito** | Há busca de contas nas listas e anotações por conta; a ficha unificada ficou fora. |
| 12 | Relatório "retrospectiva" em PDF | Premium | **Não feito** | Existem relatórios de resumo, comparativo e de qualidade com a base `DocumentoPdf`; a retrospectiva específica não foi desenhada. |

Também entregues além da lista: etiquetas, favoritos, notas, múltiplos perfis, backup protegido por senha, explorador de dados e painéis personalizados.
