# Processo de release

> Estado atual: **nenhuma versão foi lançada**. Este documento define o que precisa ser verdade antes de publicar um instalador.

## Portões obrigatórios (todos)

| # | Portão | Situação em 2026-10-28 |
|---|---|---|
| 1 | Suíte completa verde no Linux e no Windows (CI) | atendido no commit atual; análise estática (SpotBugs) incluída |
| 2 | Avaliação de Segurança 3 sem achados críticos/altos abertos (`security/FINAL_SECURITY_AUDIT.md`) | **atendido** (2026-10-28) |
| 3 | Auditoria de vulnerabilidades das dependências executada e tratada (R14/V-11) | **atendido** na CI (OSV), com 2 exceções datadas até 2026-12-15; reexecutar no commit de release |
| 4 | Verificação de segredos no índice e no histórico limpa | atendido no commit atual |
| 5 | Chave de produção de licenças gerada em ambiente controlado e a pública embutida (`docs/LICENSING.md`) | **pendente** |
| 6 | Instalador **assinado** e assinatura verificada com `signtool verify /pa` | **pendente** (sem certificado) |
| 7 | Teste manual em Windows 10 e 11 limpos: instalar, importar, atualizar, desinstalar | **pendente** |
| 8 | Revisão jurídica de licenças (`THIRD_PARTY.md`), termos e política de privacidade | pendente |
| 9 | SBOM (CycloneDX) gerado e anexado | gerado pela CI; anexar à release |
| 10 | Notas de versão e `SHA256SUMS.txt` publicados com o instalador | pendente |

## Pagamentos reais

Permanecem **desabilitados**. Mudar `PAYMENTS_MODE` para produção exige, por escrito neste repositório: provedor escolhido com documentação oficial lida, adaptador implementado e revisado, testes no modo de teste do provedor, webhook em TLS, e **aprovação explícita do responsável pelo produto** registrada em um commit dedicado. Ver `payments/PROVIDER_DECISION.md`.

## Passos

1. Congelar `main` no commit candidato; confirmar portões 1–4.
2. Criar a tag `vX.Y.Z` (mensagem em português) a partir de `main`.
3. No ambiente de release (com o certificado), rodar `scripts\empacotar-windows.ps1 -Tipo msi -Assinar`.
4. Rodar `scripts\testar-pacote-windows.ps1` sobre o instalado e a verificação manual do portão 7.
5. Publicar o instalador, o ZIP, `SHA256SUMS.txt` e o SBOM na release do GitHub.
6. Registrar o resultado no `security/REMEDIATION_REPORT.md`.

## Regras

- Nada é publicado a partir de `desenvolvimento`.
- Segredos de assinatura e de produção nunca entram no repositório.
- Se um portão falhar, o release é adiado; portões não são removidos para cumprir prazo.
