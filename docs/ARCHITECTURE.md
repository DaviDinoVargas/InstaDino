# Arquitetura — InstaDino

Este documento descreve a arquitetura **alvo** e marca o estado real de cada parte. Seções marcadas como *planejado* ainda não existem no código.

## 1. Visão geral

```
┌────────────────────────── Computador do usuário ──────────────────────────┐
│ desktop-app (JavaFX)                                                      │
│    │ casos de uso                                                         │
│ application-core ── instagram-importer ── analytics-engine                │
│    │                                                                      │
│ persistence (SQLite cifrado, migrações)    licensing-client (valida licença)│
└──────────────────────────────┬────────────────────────────────────────────┘
                               │ HTTPS (somente conta, licença, pagamento)
                               ▼
                ┌──────────── Servidor do operador ────────────┐
                │ backend-api (Spring Boot) → banco comercial │ ← webhook ← provedor
                └──────────────────────────────────────────────┘
```

Regra central: **os dados da exportação nunca atravessam a seta HTTPS.** O cliente comercial recebe apenas: credenciais da conta, identificador de dispositivo e identificadores de licença.

## 2. Módulos Maven

| Módulo | Responsabilidade | Depende de | Estado |
|---|---|---|---|
| `payment-contracts` | DTOs e contratos comerciais compartilhados (planos, licença, eventos, `ProvedorPagamento`) | — | planejado |
| `application-core` | Modelos de domínio (`NomeDeUsuario`, `Relacionamento`), catálogo de funcionalidades, política Free/Premium | — | **implementado** |
| `instagram-importer` | Validação e inventário do ZIP, parsers, normalização | `application-core` | **implementado** (relacionamentos) |
| `analytics-engine` | Comparação de conjuntos, indicadores sociais, CSV | `application-core` | **implementado** (sem PDF e gráficos) |
| `persistence` | SQLite cifrado, migrações, repositórios, proteção de chave | `application-core` | **implementado** |
| `application-services` | Casos de uso que combinam importador, análise, persistência e política | todos os acima | **implementado** (ver nota) |
| `shared-test-fixtures` | Exportações sintéticas e ZIPs maliciosos (somente teste) | — | **implementado** |
| `licensing-client` | Validação de licença assinada, cache seguro, cliente da API | `payment-contracts` | planejado |
| `backend-api` | Autenticação, sessões, licenças, pagamentos, auditoria (domínio e persistência em pacotes internos) | `payment-contracts` | planejado |
| `desktop-app` | JavaFX, navegação, telas, composição | todos os módulos do cliente | planejado |
| `packaging` | `jpackage`, instalador, assinatura | `desktop-app` | planejado |

**Nota sobre `application-services`:** os casos de uso ficam em módulo próprio, e não em `application-core`, porque dependem do importador, do motor de análise e da persistência, que por sua vez dependem de `application-core`; colocá-los lá criaria dependência circular.

**Desvio da estrutura de referência:** `backend-domain` e `backend-persistence` não são módulos separados nesta etapa; viram pacotes de `backend-api` para evitar módulos que só existam para cumprir a lista. Podem ser extraídos se o backend crescer (ADR-0003).

Dependências são acíclicas e `desktop-app` não acessa SQLite diretamente: usa interfaces de repositório de `application-core`.

## 3. Decisões técnicas (resumo)

| ADR | Decisão |
|---|---|
| 0001 | Java 21 LTS, JavaFX 25, Maven multimódulo |
| 0002 | SQLite com SQLite3 Multiple Ciphers (`io.github.willena:sqlite-jdbc`); chave aleatória protegida por DPAPI via JNA |
| 0003 | Backend Spring Boot 3.5.x; PostgreSQL em produção, H2 em testes, migrações Flyway |
| 0004 | Senhas com Argon2id (Spring Security); sessões com token opaco armazenado como hash |
| 0005 | Licença = JSON assinado Ed25519 (JDK), `kid` para rotação |
| 0006 | Pagamentos atrás da abstração `ProvedorPagamento`; provedor Sandbox primeiro; produção desabilitada por padrão |
| 0007 | Jackson para JSON, jsoup para HTML, Commons CSV; ZIP lido via `java.util.zip` com limites próprios |

Detalhes em `docs/adr/`.

## 4. Fluxos principais

### Importação (planejado)
1. Selecionar ZIP → validar tamanho/estrutura → inventário (sem extrair para disco) → confirmação de categorias sensíveis → parsing em área controlada → normalização/validação → resumo → persistência transacional.

### Controle de acesso (planejado)
- `CatalogoFuncionalidades` define ids estáveis (`SEGUIDORES_BASICO`, `HISTORICO_COMPLETO`, …).
- `PoliticaDeAcesso` decide por `EstadoDeLicenca` validado criptograficamente. A UI apenas reflete; os casos de uso reaplicam a política.

### Licença (planejado)
- Servidor emite `{payload, assinatura, kid}`; cliente valida e guarda em cache protegido; ausência de servidor mantém o último estado válido até o fim da tolerância.

### Pagamento (planejado)
- Desktop pede checkout ao backend → backend cria sessão no provedor → usuário paga na página hospedada → provedor envia webhook assinado → backend valida, aplica idempotência e ordem → ativa licença → desktop consulta o backend.

## 5. Observabilidade e erros

Logs estruturados sem dados pessoais nem segredos. Mensagens da UI são genéricas; códigos de erro internos permitem diagnóstico sem expor detalhes.
