# Estratégia de Testes

Estado real em 2026-10-26: **231 testes automatizados passam** (1 ignorado: DPAPI, que só existe no Windows).

| Módulo | Testes | O que cobre |
|---|---|---|
| application-core | 66 | política Free/Premium (incluindo licença expirada/revogada), identificadores estáveis, normalização de usuários e URLs |
| persistence | 49 (+1 ignorado) | cifragem real do arquivo, chave errada/ausente, injeção de SQL, migrações (checksum, rollback, banco mais novo), queda real do processo (`Runtime.halt`), concorrência, backup/restauração, exclusão, varredura estática de SQL |
| instagram-importer | 43 | importação válida, JSON/HTML malformados, ZIPs maliciosos (traversal, absoluto, duplicatas, bomba, truncado, entradas demais), cancelamento, tempo máximo, nada gravado fora do esperado |
| analytics-engine | 26 | conjuntos, comparação entre importações, CSV (fórmulas, acentos, controle), 200 mil registros |
| application-services | 18 | fluxo completo com banco cifrado: Free x Premium, histórico persistente, dados preservados sem licença, isolamento entre perfis, exclusão total, 180 mil registros |
| desktop-app | 29 | navegação, fluxo de importação pela UI, erros legíveis, listas paginadas (50 mil), Free x Premium, tema, redimensionamento; gera capturas em `desktop-app/target/capturas` |

## Como rodar

```bash
xvfb-run -a mvn -B verify      # tudo, com display virtual
mvn -B verify                  # sem display: testes de interface IGNORADOS (não aprovados)
```

## Garantias de qualidade dos testes

- Testes de segurança do importador foram submetidos a **mutação manual**: remover o bloqueio de `..`, o limite real de bytes e a detecção de duplicatas fez os testes correspondentes falharem.
- Nenhum teste usa exportações reais; todos usam dados sintéticos (`shared-test-fixtures`).
- Nenhum teste faz cobrança ou acessa serviço externo.

## Lacunas conhecidas

- Sem execução em Windows real (DPAPI, instalador, caminhos).
- Sem *fuzzing* contínuo do importador.
- Sem testes de autenticação, licenciamento e pagamentos: esses módulos ainda não existem.
- Cobertura medida com JaCoCo (relatório em `target/site/jacoco`), sem meta numérica aplicada ainda.
