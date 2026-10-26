# Plano de Segurança — InstaDino

## 1. Objetivo

Reduzir a superfície de ataque com defesa em profundidade, testar os controles e documentar os riscos residuais. Este plano organiza **quando** e **como** a segurança é verificada.

## 2. Pontos de controle obrigatórios

| Avaliação | Momento | Entregáveis |
|---|---|---|
| 1 | Início (este documento e irmãos) | `THREAT_MODEL`, `SECURITY_REQUIREMENTS`, `RISK_REGISTER`, `SECURITY_PLAN` |
| 2 | Após importação, persistência, autenticação, licenciamento e pagamentos em sandbox | Relatório com vulnerabilidades, severidade, evidências, correções e riscos pendentes |
| 3 | Pré-lançamento | `FINAL_SECURITY_AUDIT`, `REMEDIATION_REPORT`, `RELEASE_SECURITY_CHECKLIST` |

Achados críticos e altos são corrigidos antes de avançar. Exceções exigem justificativa, mitigação e aprovação explícita do responsável pelo produto.

## 3. Atividades contínuas (cada branch)

1. Revisão do diff antes de cada commit.
2. Execução dos testes do módulo alterado e da suíte completa antes da integração.
3. `scripts/verificar-segredos.sh` (também no CI) sobre o índice do Git.
4. Verificação de que nenhum arquivo proibido (`*.zip` real, `*.db`, `*.pem`, `.env`) está no índice.
5. Registro de decisões relevantes em `docs/adr/`.

## 4. Ferramentas

| Finalidade | Ferramenta | Observação |
|---|---|---|
| Análise estática | SpotBugs (+ regras de segurança quando compatíveis) | Executado no CI |
| Dependências | OWASP Dependency-Check | Requer acesso ao banco NVD; pode falhar sem rede/chave — nesse caso é registrado, não ignorado em silêncio |
| Licenças | Inventário gerado e revisado manualmente | Resultado em `docs/THIRD_PARTY.md` |
| SBOM | CycloneDX Maven Plugin | Anexado à release |
| Segredos | Script próprio de padrões + revisão do histórico | Complementa, não substitui, revisão humana |
| Testes | JUnit 5; AssertJ | Sem Docker nos testes básicos |
| Cobertura | JaCoCo | Meta por risco, nunca como prova isolada de segurança |

## 5. Metas de cobertura (linha de base, revisáveis)

| Módulo | Meta de linhas | Critério adicional |
|---|---|---|
| instagram-importer | ≥ 85% | Todos os casos IMP-01…IMP-09 com teste nominal |
| licensing-client | ≥ 90% | Cada campo adulterado tem teste |
| backend (auth, licença, pagamento) | ≥ 85% | Testes de isolamento entre contas |
| persistence | ≥ 80% | Migração, rollback e cifragem verificados |
| analytics-engine | ≥ 85% | Propriedades de conjuntos |

## 6. Gestão de segredos

- Nenhum segredo no repositório (público). Configuração por variáveis de ambiente (`.env.exemplo` apenas com nomes e valores fictícios).
- Chave privada de licenças, segredo de webhook e credenciais do provedor existem só no ambiente do backend.
- Certificado de assinatura de código só no processo de release.
- Contas iniciais (master, premium, free) são criadas por *script* de semeadura que **gera senhas aleatórias** e as entrega ao responsável de forma local (arquivo ignorado pelo Git). Senhas nunca são commitadas.
- Se um segredo for publicado: revogar/rotacionar primeiro; sanear histórico depois.

## 7. Resposta a incidentes (resumo)

1. Conter (revogar credenciais/sessões/licenças afetadas).
2. Avaliar escopo pelos registros de auditoria.
3. Corrigir e adicionar teste de regressão.
4. Comunicar usuários afetados conforme LGPD quando aplicável.
5. Registrar no relatório de remediação.

## 8. Critérios para avançar entre fases

- Fase 2 → 3: persistência cifrada com testes DB-01/02/04/05 passando.
- Fase 3 → 4: importador com IMP-01…IMP-09 testados.
- Fase 5 → 6: Avaliação 2 concluída; nenhum achado crítico/alto aberto.
- Release: Avaliação 3 sem crítico/alto; instalador testado em Windows real; assinatura verificada.
