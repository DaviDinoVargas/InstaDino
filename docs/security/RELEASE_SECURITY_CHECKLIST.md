# Checklist de segurança do lançamento

Marque cada item **somente** com a evidência indicada. Se um item não puder ser marcado, o lançamento é adiado; itens não são removidos para cumprir prazo. Estado em 2026-10-28.

| # | Item | Evidência exigida | Estado |
|---|---|---|---|
| 1 | Suíte completa verde no Linux e no Windows | Execuções verdes da CI no commit candidato | [x] atendido no commit atual |
| 2 | Análise estática sem achados | `BugInstance size is 0` em todos os módulos | [x] |
| 3 | Auditoria de dependências verde, exceções vigentes e revisadas | Job OSV verde; `config/osv-excecoes.json` sem data vencida | [x] com 2 exceções até 2026-12-15 |
| 4 | SBOM CycloneDX anexado à release | `target/bom.json` e `bom.xml` da CI | [ ] gerar no commit de release |
| 5 | Varredura de segredos no índice e no histórico limpa | `scripts/verificar-segredos.sh --historico` | [x] |
| 6 | Avaliação de Segurança 3 sem achado crítico ou alto aberto | `FINAL_SECURITY_AUDIT.md` | [x] |
| 7 | Chave de produção de licenças gerada em ambiente controlado; só a pública embutida | `docs/LICENSING.md`, revisão por duas pessoas | [ ] **bloqueia** |
| 8 | Instalador **assinado** e verificado | `signtool verify /pa` na saída do release | [ ] **bloqueia** |
| 9 | Teste manual em Windows 10 e 11 limpos: instalar, importar, atualizar, desinstalar | Registro assinado pelo testador | [ ] **bloqueia** |
| 10 | Importação testada com uma exportação real de cada categoria usada (atividade, conteúdo, anúncios, mensagens) e diagnóstico conferido | Registro do responsável, sem dados pessoais | [ ] recomendado; os relacionamentos já foram conferidos |
| 11 | Revisão jurídica de licenças (`THIRD_PARTY.md`), termos e privacidade | Parecer | [ ] **bloqueia** |
| 12 | Pagamentos: modo de produção continua desabilitado **ou** provedor escolhido, adaptador revisado, testes no modo de teste, webhook em TLS e aprovação explícita em commit dedicado | `docs/PAYMENTS.md` | [x] desabilitado; nenhuma cobrança real é possível |
| 13 | `SHA256SUMS.txt` e notas de versão publicados com o instalador | Release do GitHub | [ ] no momento do release |
| 14 | Contas de demonstração só em perfil de desenvolvimento, senhas fora do repositório | Revisão do perfil de produção | [x] |
| 15 | Resultado registrado em `REMEDIATION_REPORT.md` | Commit | [ ] no momento do release |
