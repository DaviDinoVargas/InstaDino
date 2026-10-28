# Modelo de Ameaças — InstaDino

Versão: 1.0 (Avaliação de Segurança 1 — início do projeto)
Metodologia: STRIDE aplicado a cada limite de confiança, com priorização por impacto e probabilidade (ver `RISK_REGISTER.md`).

> Nenhum sistema é invulnerável. Este documento descreve o que se pretende proteger, de quem, e com quais controles. Os riscos residuais são declarados explicitamente.

## 1. Escopo e componentes

| Componente | Onde executa | Confiança |
|---|---|---|
| Aplicativo desktop JavaFX | Computador do usuário (Windows) | Não confiável para decisões comerciais (o usuário controla a máquina) |
| Núcleo local (importador, análise, SQLite) | Computador do usuário | Confiável apenas para dados do próprio usuário |
| Backend comercial (API) | Servidor do operador | Fonte da verdade para contas, licenças e pagamentos |
| Banco comercial | Servidor do operador, rede privada | Acessível somente pelo backend |
| Provedor de pagamento | Terceiro | Confiável para o estado financeiro, após verificação de autenticidade |
| Arquivo ZIP da exportação | Origem: usuário | **Não confiável** (entrada hostil potencial) |

## 2. Ativos

| Id | Ativo | Sensibilidade |
|---|---|---|
| A1 | Exportação do Instagram (listas, mensagens, mídia, atividade) | Alta — dados pessoais de terceiros inclusive |
| A2 | Banco SQLite local | Alta |
| A3 | Chave de cifragem do banco local | Crítica |
| A4 | Credenciais de conta comercial (senha, tokens de sessão) | Crítica |
| A5 | Chave privada de assinatura de licenças | Crítica |
| A6 | Segredos do provedor de pagamento e do webhook | Crítica |
| A7 | Banco comercial (contas, licenças, transações) | Alta |
| A8 | Integridade das licenças (não conceder Premium indevidamente) | Alta (negócio) |
| A9 | Pipeline de build, certificado de assinatura de código | Crítica |
| A10 | Repositório público no GitHub | Média — não pode conter segredos |

## 3. Agentes de ameaça

- **T1 Usuário malicioso**: quer Premium sem pagar (patch do executável, edição de arquivos, falsificação de respostas).
- **T2 Atacante remoto não autenticado**: força bruta, enumeração de contas, abuso de webhooks, injeção.
- **T3 Usuário autenticado malicioso**: tenta acessar dados/licenças de outra conta (IDOR), escalar privilégio.
- **T4 Autor de ZIP malicioso**: o usuário é induzido a importar ZIP forjado (path traversal, ZIP bomb).
- **T5 Malware/outro usuário local**: lê banco, backups, temporários ou memória no computador do usuário.
- **T6 Cadeia de suprimentos**: dependência comprometida, artefato adulterado, segredo vazado no GitHub.
- **T7 Falsificador de webhook / replay**: simula confirmação de pagamento.
- **T8 Insider/operador**: acesso excessivo ao banco comercial.

## 4. Pontos de entrada

1. Seleção de arquivo ZIP (E1).
2. Campos de interface: busca, filtros, notas, etiquetas (E2).
3. API comercial: cadastro, login, recuperação de senha, sessões, licenças, checkout (E3).
4. Endpoint de webhook do provedor (E4).
5. Arquivos de configuração e diretório de dados locais (E5).
6. Canal de atualização/instalador (E6).
7. Dependências Maven e pipeline CI (E7).
8. Exportações CSV/PDF abertas em outros programas (E8).

## 5. Limites de confiança

```
[Usuário] --E1--> [Importador ZIP] ==fronteira A==> [Núcleo/SQLite cifrado]
[Desktop]  --TLS, token--> ==fronteira B==> [API comercial] ==fronteira C==> [Banco comercial]
[Provedor] --webhook assinado--> ==fronteira D==> [API comercial]
[Desktop]  <--licença assinada Ed25519-- [API comercial]
```

- **A**: tudo que vem do ZIP é dado hostil até ser validado e normalizado.
- **B**: o desktop é cliente não confiável; toda autorização é reavaliada no servidor.
- **C**: o banco comercial nunca é exposto; conta de aplicação com privilégios mínimos.
- **D**: evento só é aceito após verificação de assinatura, janela de tempo e idempotência.

## 6. Dados pessoais e financeiros

| Dado | Local | Sai do computador? |
|---|---|---|
| Listas de seguidores, mensagens, mídia, atividade | SQLite local cifrado / diretório de dados | **Nunca** |
| E-mail e hash da senha | Backend | Sim (necessário para conta) |
| Estado da licença/assinatura | Backend e cache local assinado | Sim (mínimo) |
| Dados de cartão/CVV/bancários | Provedor (checkout hospedado) | Nunca tocam o InstaDino |
| Identificadores de cliente/assinatura do provedor | Backend | Sim |

## 7. Análise STRIDE por limite

### Fronteira A — Importador ZIP

| STRIDE | Ameaça | Controle planejado |
|---|---|---|
| T (Tampering) | Path traversal (`../`, caminho absoluto, `C:\`, separadores mistos, links) | Normalização e rejeição; nunca extrair para caminho vindo do ZIP; leitura em stream para memória/área controlada |
| D (DoS) | ZIP bomb, milhões de entradas, entradas gigantes | Limites de tamanho do ZIP, nº de entradas, tamanho descompactado total e por entrada, razão de compressão, profundidade, tempo e cancelamento |
| T | Entradas duplicadas / nomes ambíguos (Unicode, maiúsculas) | Rejeição de duplicatas pós-normalização |
| E (Elevation) | Conteúdo interpretado como código | Apenas parsing de dados (JSON/HTML via parser seguro); nenhuma execução; HTML tratado como texto |
| I (Info disclosure) | Mensagens de erro revelando caminhos | Erros genéricos para a UI; detalhe somente em log sem dados pessoais |
| T | Falha parcial corrompe histórico | Importação transacional; commit somente após validação e confirmação |
| D | XXE / entidades externas em XML/HTML | Sem parser XML com entidades; jsoup para HTML; Jackson com limites de profundidade/tamanho |

### Fronteira B — Desktop ↔ API

| STRIDE | Ameaça | Controle planejado |
|---|---|---|
| S (Spoofing) | Login por força bruta/credential stuffing | Argon2id, limitação de taxa por IP e por conta, bloqueio progressivo, resposta uniforme |
| S | Enumeração de contas | Mensagens e tempos uniformes em login/recuperação/cadastro |
| S | Roubo de token de sessão | Tokens opacos aleatórios armazenados como hash no servidor; expiração; revogação; no desktop protegidos por DPAPI |
| E | IDOR / acesso a licença de outra conta | Autorização por recurso no servidor (dono = principal autenticado); testes de isolamento |
| E | Escalonamento de papel (usuário → master) | Papéis só no servidor; endpoints administrativos exigem papel; sem confiar em campos do cliente |
| R (Repudiation) | Ações administrativas sem rastro | Trilha de auditoria imutável (append-only) sem segredos |
| T | MITM | TLS obrigatório; cliente recusa HTTP fora do perfil de desenvolvimento local |
| I | Segredos em logs | Filtro de logs; testes que verificam ausência de tokens/senhas |

### Fronteira D — Webhook de pagamento

| STRIDE | Ameaça | Controle planejado |
|---|---|---|
| S | Webhook forjado concede Premium | Verificação HMAC com comparação em tempo constante; segredo só no backend; rejeição de timestamp fora da janela |
| T | Replay | Tabela de eventos processados (id único) + janela de tempo |
| T | Eventos fora de ordem (ex.: "cancelado" antes de "pago") | Máquina de estados da assinatura com versão/horário do evento do provedor; evento mais antigo não reverte estado mais novo |
| E | Cliente declara pagamento confirmado | Endpoint inexistente; Premium só emitido pelo backend após evento verificado ou consulta ao provedor |
| T | Preço adulterado | Preço sempre vindo do catálogo do servidor/provedor |
| D | Inundação do endpoint | Limite de taxa, corpo máximo, validação antes de processar |

### Licenciamento

| Ameaça | Controle |
|---|---|
| Edição de arquivo/flag local para virar Premium | Licença é documento assinado Ed25519 pelo servidor; cliente valida assinatura, produto, plano, titular, validade, `kid` |
| Substituição da chave pública no executável | Aceito como risco residual (T1 com acesso total à máquina); mitigado por assinatura de código e verificação de integridade, sem promessa de proteção absoluta |
| Reaproveitamento de licença em muitos dispositivos | Vínculo a dispositivos autorizados, limite configurável, revogação |
| Relógio do sistema adulterado para estender offline | Período de tolerância offline limitado, com último horário confiável visto do servidor e verificação de regressão do relógio |
| Chave privada de assinatura vazada | Fora do repositório; rotação por `kid`; plano de revogação |

### Banco local / computador do usuário (T5)

| Ameaça | Controle | Limite honesto |
|---|---|---|
| Leitura do arquivo `.db` | Cifragem em repouso (SQLite3 Multiple Ciphers, ChaCha20-Poly1305/SQLCipher-compat), chave aleatória por instalação | Malware com a sessão do usuário pode usar DPAPI e obter a chave |
| Chave fixa no código | Proibida; chave gerada com `SecureRandom` e protegida por DPAPI | Perfil Windows compartilhado/admin: não protegido |
| Temporários e backups em claro | Diretório controlado, permissões restritas, limpeza em falha; backup cifrado | Remanência em SSD pode não ser eliminada por exclusão |
| SQL injection | Somente `PreparedStatement`; teste dedicado | — |

### Cadeia de suprimentos (T6)

- Versões fixas no POM; auditoria OWASP Dependency-Check e revisão de licenças.
- Verificação de segredos no CI e *pre-commit*; `.gitignore` não é suficiente, o índice e o histórico são inspecionados.
- Repositório é **público**: nenhum segredo, senha, token ou ZIP real pode ser commitado.
- Instalador assinado (certificado fora do repositório) e SHA-256 publicado; SBOM CycloneDX.

## 8. Ameaças fora de escopo / limites declarados

- Proteção absoluta contra engenharia reversa do desktop.
- Computador do usuário totalmente comprometido ou com administrador malicioso.
- Garantia de que o ZIP baixado do Instagram é autêntico: o app valida estrutura, não a procedência junto à plataforma.
- O app não usa endpoints privados do Instagram nem scraping.

## 9. Premissas

1. O backend roda atrás de TLS terminado em proxy confiável em produção.
2. O banco comercial (PostgreSQL) não é exposto à internet.
3. Credenciais reais do provedor ainda não existem; tudo é validado em sandbox.
4. O pagamento real permanece **desabilitado** por padrão (`falha fechada`).
