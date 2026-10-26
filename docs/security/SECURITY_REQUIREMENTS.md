# Requisitos de Segurança — InstaDino

Cada requisito tem identificador estável, critério de verificação e fase em que será verificado. "Verificação" descreve o teste ou a revisão que comprova o controle. Um requisito só é marcado como atendido quando o teste existe e passa.

Legenda de severidade se o requisito falhar: **C** crítica, **A** alta, **M** média.

## 1. Importação de arquivos (IMP)

| Id | Requisito | Sev. | Verificação |
|---|---|---|---|
| IMP-01 | Rejeitar entradas com `..`, caminho absoluto, unidade (`C:`), `\` e separadores mistos após normalização | C | Testes unitários com ZIPs maliciosos sintéticos |
| IMP-02 | Nunca extrair para caminho derivado do ZIP; processar em stream ou em diretório controlado com destino validado | C | Teste: nada é gravado fora da área controlada |
| IMP-03 | Limites configuráveis: tamanho do ZIP, nº de entradas, tamanho descompactado total e por entrada, razão de compressão, profundidade | A | Testes com ZIP bomb sintética |
| IMP-04 | Rejeitar entradas duplicadas (inclusive após normalização Unicode/caixa) | A | Teste |
| IMP-05 | Nenhum conteúdo importado é executado ou interpretado como código | C | Revisão + teste de fuzz simples |
| IMP-06 | Importação transacional: falha ou cancelamento não altera o histórico | A | Teste com falha injetada |
| IMP-07 | Limite de tempo e cancelamento cooperativo | M | Teste |
| IMP-08 | Arquivos temporários removidos em qualquer desfecho | M | Teste |
| IMP-09 | Parsers JSON com limites de profundidade e tamanho de string; HTML tratado como texto | A | Testes |

## 2. Armazenamento local (DB)

| Id | Requisito | Sev. | Verificação |
|---|---|---|---|
| DB-01 | Banco SQLite cifrado em repouso por biblioteca mantida (sem criptografia própria) | C | Teste: o arquivo não contém texto em claro nem o cabeçalho `SQLite format 3` |
| DB-02 | Chave aleatória por instalação, nunca embutida no código ou no repositório | C | Revisão + teste de varredura de segredos |
| DB-03 | Chave protegida por DPAPI no Windows; sem DPAPI o app recusa criar banco em modo produção | A | Teste (unidade com adaptador); verificação em Windows real pendente de ambiente |
| DB-04 | Somente consultas parametrizadas | C | Teste de injeção + busca estática por concatenação SQL |
| DB-05 | Migrações versionadas com checksum; falha de migração não deixa banco parcial | A | Testes de migração |
| DB-06 | Verificação de integridade (`PRAGMA integrity_check`) e `foreign_keys=ON` | M | Teste |
| DB-07 | Backup cifrado, restauração testada | A | Teste |
| DB-08 | Exclusão de dados do usuário por categoria e total | A | Teste |
| DB-09 | Logs e mensagens de erro sem dados pessoais | A | Teste de logs |

## 3. Autenticação e sessão (AUT)

| Id | Requisito | Sev. | Verificação |
|---|---|---|---|
| AUT-01 | Senhas com Argon2id, sal individual, parâmetros documentados | C | Teste |
| AUT-02 | Política de senha (comprimento mínimo ≥ 12, lista de senhas comuns) | M | Teste |
| AUT-03 | Limitação de taxa e bloqueio progressivo por IP e por conta | A | Teste |
| AUT-04 | Respostas uniformes que não revelam existência de conta (login, cadastro, recuperação) | A | Teste |
| AUT-05 | Tokens de sessão aleatórios (≥ 256 bits), guardados como hash, com expiração e revogação | C | Teste |
| AUT-06 | Recuperação de senha com token de uso único, expiração curta, hash em repouso; revoga sessões | A | Teste |
| AUT-07 | Nenhum segredo em log | A | Teste |
| AUT-08 | TLS obrigatório fora do perfil de desenvolvimento local; configuração insegura impede a inicialização em produção | A | Teste de configuração |
| AUT-09 | Contas semeadas não possuem senha fixa no repositório | C | Varredura de segredos + revisão |

## 4. Autorização (AUZ)

| Id | Requisito | Sev. | Verificação |
|---|---|---|---|
| AUZ-01 | Toda operação sensível autorizada no servidor por papel e por propriedade do recurso | C | Testes de isolamento entre contas |
| AUZ-02 | Papel `MASTER` somente atribuído no servidor; não aceito de entrada do cliente | C | Teste |
| AUZ-03 | Catálogo de funcionalidades com ids estáveis, aplicado também na camada de aplicação desktop | A | Teste |
| AUZ-04 | Ações administrativas auditadas (append-only) | A | Teste |

## 5. Licenciamento (LIC)

| Id | Requisito | Sev. | Verificação |
|---|---|---|---|
| LIC-01 | Licença é documento assinado Ed25519; cliente valida assinatura, produto, plano, titular, emissão, validade, `kid` | C | Testes com adulteração de cada campo |
| LIC-02 | Chave privada somente no backend; cliente possui apenas chave(s) pública(s) | C | Revisão + varredura |
| LIC-03 | Flag/arquivo/preferência local nunca concede Premium | C | Teste |
| LIC-04 | Tolerância offline limitada; detecção de regressão de relógio | A | Teste |
| LIC-05 | Licença expirada/sem servidor nunca bloqueia acesso aos dados locais | A | Teste |
| LIC-06 | Revogação e limite de dispositivos | A | Teste |

## 6. Pagamentos (PAG)

| Id | Requisito | Sev. | Verificação |
|---|---|---|---|
| PAG-01 | Cliente nunca confirma pagamento; não existe endpoint que aceite essa declaração | C | Teste |
| PAG-02 | Webhook: verificação HMAC em tempo constante, janela de tempo, corpo bruto | C | Testes (inválido, vencido, adulterado) |
| PAG-03 | Idempotência por id de evento | C | Teste de evento repetido |
| PAG-04 | Eventos fora de ordem não concedem nem restauram licença indevidamente | C | Teste |
| PAG-05 | Preço e plano determinados no servidor | A | Teste |
| PAG-06 | Falha fechada: `PAYMENTS_ENABLED=false` por padrão; modo produção exige configuração completa e validada | C | Testes de configuração |
| PAG-07 | Nenhum dado de cartão trafega ou é armazenado | C | Revisão de modelo de dados |
| PAG-08 | Reembolso e chargeback revogam/ajustam licença conforme política | A | Teste |
| PAG-09 | Testes nunca realizam cobrança real | C | Revisão; modo sandbox não possui chave de produção |

## 7. Repositório e cadeia de suprimentos (SUP)

| Id | Requisito | Sev. | Verificação |
|---|---|---|---|
| SUP-01 | Nenhum segredo, ZIP real, banco ou log versionado | C | `scripts/verificar-segredos` no CI e *hook* local |
| SUP-02 | Versões de dependências fixadas; auditoria de vulnerabilidades no CI | A | OWASP Dependency-Check |
| SUP-03 | Inventário de licenças e SBOM | M | CycloneDX |
| SUP-04 | Instalador assinado e SHA-256 publicado | A | Checklist de release (exige Windows) |
| SUP-05 | Nenhuma menção a ferramentas de geração de código ou coautoria automática no repositório | M | Verificação no CI |

## 8. Privacidade (PRI)

| Id | Requisito | Sev. | Verificação |
|---|---|---|---|
| PRI-01 | Nenhum dado da exportação é enviado ao backend | C | Teste de arquitetura (o cliente HTTP não referencia módulos de dados) + revisão |
| PRI-02 | Módulo de mensagens desativado por padrão e com consentimento explícito | A | Teste |
| PRI-03 | Sem telemetria sensível por padrão | A | Revisão |
| PRI-04 | Relatórios não incluem dados sensíveis por padrão; CSV neutraliza fórmulas | M | Teste |
