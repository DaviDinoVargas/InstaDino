# Estratégia de Testes

Estado real em 2026-10-28: **693 testes automatizados** executados, 0 falhas, 1 ignorado (DPAPI, que só existe no Windows; ele roda na CI do Windows).

| Módulo | Testes | O que cobre |
|---|---|---|
| application-core | 66 | política Free/Premium (licença expirada/revogada), consentimento de módulos sensíveis, identificadores estáveis, normalização de usuários e URLs |
| payment-contracts | 34 | contratos, estados e validações dos DTOs comerciais |
| persistence | 83 (+1 ignorado) | cifragem real do arquivo, chave errada/ausente, injeção de SQL, migrações V001–V005 (checksum, rollback, banco mais novo), queda real do processo, concorrência, backup portátil com senha, exclusão, varredura estática de SQL, repositórios de atividade, conteúdo, anotações e conversas |
| licensing-client | 45 | validação Ed25519, relógio, cache, adulteração, rotação de chave |
| backend-api | 104 | autenticação, sessões, autorização (acesso entre contas), licenças, webhooks assinados, idempotência e corrida de eventos, produção de pagamentos desabilitada |
| instagram-importer | 182 | ZIPs maliciosos, JSON/HTML malformados, limites, relacionamentos (`label_values`, registro único, exportação parcial), atividade, conteúdo, anúncios, mensagens (nunca o texto), diagnóstico sem dados pessoais |
| analytics-engine | 39 | conjuntos, comparação, CSV (injeção de fórmula, acentos), 200 mil registros |
| application-services | 67 | fluxo completo com banco cifrado: Free x Premium, histórico, anotações, perfis, relatórios PDF, atividade, conteúdo, mensagens (consentimento, revogação, plano expirado), explorador, painéis, backup |
| desktop-app | 73 | navegação, importação pela UI (incluindo consentimento de mensagens), erros legíveis, listas paginadas, gráficos, relatórios, Free x Premium, tema; gera capturas em `desktop-app/target/capturas` |

## Como rodar

```bash
xvfb-run -a mvn -B verify      # tudo, com display virtual
mvn -B verify                  # sem display: testes de interface IGNORADOS (não aprovados)
```

## Garantias de qualidade dos testes

- Testes de segurança do importador foram submetidos a **mutação manual**: remover o bloqueio de `..`, o limite real de bytes e a detecção de duplicatas fez os testes correspondentes falharem.
- Nenhum teste usa exportações reais; todos usam dados sintéticos (`shared-test-fixtures`).
- Nenhum teste faz cobrança ou acessa serviço externo.

## Execução em Windows

O fluxo `.github/workflows/windows.yml` roda a suíte completa em `windows-latest`, gera o app-image e o MSI, prova por um teste de fumaça que o banco fica cifrado e a chave fica protegida por DPAPI, e instala, executa e desinstala o MSI conferindo que os dados do usuário são preservados. O fluxo `ci.yml` roda a suíte no Linux com display virtual e a varredura de segredos.

## Lacunas conhecidas

- Sem teste manual em um Windows limpo (máquina virtual sem JDK) e sem teste de **atualização** do MSI entre versões.
- Atividade, conteúdo, anúncios e mensagens foram validados **apenas com exportações sintéticas**; os relacionamentos foram conferidos com uma exportação real (ver `IMPORT_FORMATS.md`).
- Sem *fuzzing* contínuo do importador.
- Pagamentos testados somente em sandbox; nenhum provedor real foi integrado.
- Cobertura medida com JaCoCo (relatório em `target/site/jacoco`), sem meta numérica aplicada.
