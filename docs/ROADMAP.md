# Roteiro de funcionalidades propostas

Origem: sugestões do responsável pelo produto após testar o aplicativo com uma exportação real (2026-10-26). **Nada abaixo está implementado.** A ordem respeita a regra do projeto: não construir nada por cima de um importador que não foi validado com dados reais.

| # | Proposta | Plano | Observações de privacidade e viabilidade |
|---|---|---|---|
| 1 | **Validar o importador com a exportação real** | — | **Primeiro passo e bloqueante.** Em andamento: diagnóstico por arquivo na tela de importação (ver `IMPORT_FORMATS.md`). |
| 2 | Não recíprocos com contexto (cruzar com curtidas, buscas, conversas) | Free/Premium | Depende dos parsers de atividade (Fase 4c). Só contagens; nunca inferir intenção. |
| 3 | Ranking de interação (curtidas, story_interactions, comentários) | Premium | Texto do tipo "curtiu N vezes", sem interpretar motivos. |
| 4 | Buscados e não seguidos (e o inverso) | Premium | Cruza `profile_searches` com as listas. |
| 5 | "Deixou de seguir" com origem declarada (`recently_unfollowed_profiles`) | Free/Premium | Coerente com a regra do produto **se** a tela disser que é um registro do próprio Instagram e cobre só um período recente. |
| 6 | Linha do tempo e mapa de calor de atividade | Premium | Só dados que já têm data. |
| 7 | "O que o Instagram sabe sobre mim" (anúncios, tópicos, logins) | Free | Alinhado ao discurso de privacidade. Alerta de login inesperado exige cuidado para não afirmar além do que o arquivo prova. |
| 8 | Auditoria de contatos sincronizados | Premium, **sensível** | Dados de **terceiros** (telefone/e-mail). Só com consentimento explícito, somente leitura local, nunca em relatórios. Maior risco de privacidade do conjunto. |
| 9 | Estatísticas de mensagens (contagens, nunca texto) | Premium, consentimento | Módulo de mensagens já previsto na Fase 4d; começar só com contagens e datas. |
| 10 | Galeria local de mídia | Premium | HEIC exige biblioteca de decodificação: afeta `THIRD_PARTY.md` e a auditoria de dependências (ainda pendente). O ZIP original precisa continuar disponível para ler as mídias. |
| 11 | Ficha do perfil (busca global) | Free | Costura as demais funções; fica por último. |
| 12 | Relatório "retrospectiva" em PDF | Premium | Reaproveita `DocumentoPdf`; depende dos dados dos itens 3 a 6. |
