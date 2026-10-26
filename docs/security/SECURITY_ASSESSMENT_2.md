# Avaliação de Segurança 2 — Meio do Projeto

Data: 2026-10-26 · Escopo: importador, persistência local cifrada, backend comercial (autenticação, licenças, pagamentos **em sandbox**), cliente de licenciamento e integração com o desktop. Commit-base: `desenvolvimento` após a integração conta/desktop.

> Não é um teste de intrusão independente. É uma revisão do próprio time com testes automatizados, testes de mutação em controles escolhidos e execução ponta a ponta local. As limitações estão na seção 5.

## 1. Método

1. Revisão do código contra `SECURITY_REQUIREMENTS.md`, controle a controle.
2. Suíte automatizada (JUnit 5): **468 testes executados, 0 falhas, 1 ignorado** (DPAPI, só roda em Windows). Por módulo: núcleo 66 · contratos de pagamento 34 · persistência 49 (+1 ignorado) · importador 43 · análise 26 · casos de uso 18 · cliente de licenciamento 43+2 · backend 102+2 · desktop 40.
3. **Testes de mutação manuais** em controles críticos: o controle é quebrado de propósito e o teste correspondente precisa falhar. Um mutante sobreviveu e revelou lacuna de teste (V-07); a correção do webhook (V-02) e o teste de arquitetura (V-10) também foram validados assim.
4. Execução ponta a ponta com o JAR empacotado: backend real em porta aleatória e cliente real; as três contas de demonstração se comportam como projetado (master e premium recebem licença assinada; free não).
5. Varredura de segredos no índice **e** no histórico (`scripts/verificar-segredos.sh --historico`): nada encontrado.

## 2. Achados

| Id | Sev. | Achado | Como foi encontrado | Correção | Estado |
|---|---|---|---|---|---|
| V-01 | **Crítica** | O driver SQLite ignorava a chave passada por `Properties`: o banco era gravado **em claro** apesar de “cifrado” | Teste que lê o arquivo em disco procurando o cabeçalho `SQLite format 3` e texto conhecido | Chave passada pelos parâmetros da URL; teste verifica a cifragem ativa no disco (`BancoLocalTest.arquivoNaoContemCabecalhoNemTextoEmClaro`) | Corrigido |
| V-02 | **Alta** | Corrida na idempotência do webhook: entidade com id atribuído era gravada por `merge`; duas entregas simultâneas do mesmo evento podiam ser processadas **duas vezes** (estender a assinatura duas vezes) | Falha intermitente de `entregaConcorrenteDoMesmoEventoProcessaExatamenteUmaVez` na suíte completa sob carga (não reproduzia isolada, 0/12) | `EventoPagamento` implementa `Persistable` (sempre `INSERT`); teste determinístico `gravarOMesmoIdDeEventoDuasVezesViolaAChavePrimaria`, que **falha sem a correção** e passa com ela | Corrigido |
| V-03 | Média | `ClienteHttp` tratava `302` com corpo vazio como sucesso | Revisão + teste de redirecionamento | Qualquer resposta não 2xx é erro; corpo vazio é erro quando se esperam dados; redirecionamentos não são seguidos | Corrigido |
| V-04 | Média | Caminho inexistente e UUID malformado na URL devolviam `500` | Testes de robustez | Tratadores para `NoHandlerFoundException` e `MethodArgumentTypeMismatchException` (404/400, sem detalhes internos) | Corrigido |
| V-05 | Média | Neutralização de fórmula em CSV avaliava o texto **depois** de converter tab em espaço (contorno com `\t=`) | Teste de caso-limite | `pareceFormula` avalia o texto bruto | Corrigido |
| V-06 | Média | `BackupLocal.restaurar` executava migrações em um banco “mais novo” enquanto só deveria verificá-lo | Teste de restauração | Abertura própria só para verificação (`abrirParaVerificacao`) | Corrigido |
| V-07 | Baixa | Lacuna de teste: reembolso revogava as licenças, mas nenhum teste afirmava isso (mutante sobreviveu) | Teste de mutação | Asserções sobre `LicencaEmitida.revogadaEm` | Corrigido |
| V-08 | Baixa | O verificador de segredos acusava o próprio arquivo de autoteste (o CI falharia) | Execução do CI | Arquivo de autoteste excluído da varredura, com teste do verificador | Corrigido |
| V-09 | Info | Um commit foi feito com a build quebrada (teste de semeadura lia a linha errada) | Revisão do CI | Teste corrigido; regra do processo: nenhum commit sem a suíte do módulo verde | Corrigido |
| V-10 | Baixa | PRI-01 (dados da exportação não vão ao backend) só era garantido por estrutura de módulos, sem teste | Revisão dos requisitos | `ArquiteturaDePrivacidadeTest` e `ArquiteturaDePrivacidadeServidorTest` inspecionam as classes compiladas; o backend usa `licensing-client` só com `scope=test` | Corrigido |
| V-11 | **Alta** | **Auditoria de vulnerabilidades de dependências não foi executada** | OSV e NVD inacessíveis pela rede do ambiente; Dependabot desativado no repositório | — | **Aberto** |

Nenhum achado crítico ou alto está aberto exceto V-11 (limitação de ambiente, não de código); ele **bloqueia o release** até ser executado (ver 4).

## 3. Situação dos requisitos

- Atendidos com teste que passa: IMP-01…IMP-09, DB-01…DB-08, AUT-01…AUT-08, AUZ-01, AUZ-02, AUZ-04, LIC-01…LIC-06, PAG-01…PAG-08 (em sandbox), PRI-01, PRI-04.
- **Parciais**: DB-03 (DPAPI: adaptador testado por unidade; chamada real exige Windows), DB-09 e AUT-07 (logs verificados nos fluxos testados, não em todos os caminhos de erro), PRI-02 (consentimento aplicado; o módulo de mensagens ainda não existe), AUZ-03 (catálogo de funcionalidades aplicado nas telas existentes).
- **Não atendidos ainda**: SUP-02/SUP-03 (auditoria de dependências, SBOM), SUP-04 (instalador assinado), PAG-09 só parcial (sandbox sem chave de produção, mas sem adaptador real a revisar).
- AUT-09: não há senha no repositório; a varredura do índice e do histórico não encontrou nenhuma.
- SUP-05: o verificador do CI procura menções proibidas no conteúdo e no histórico de commits.

O estado de cada risco está em `RISK_REGISTER.md`.

## 4. Riscos pendentes e ações

| Pendência | Ação | Prazo |
|---|---|---|
| V-11 / R14 auditoria de CVE | Rodar OWASP Dependency-Check ou OSV-Scanner em ambiente com rede; ativar Dependabot (decisão do responsável pelo repositório) | Antes do release |
| R10/DB-03 DPAPI | Rodar `ProtetorDpapiTest` e o fluxo completo em Windows real | Antes do release |
| R25/SUP-04 instalador | Build jpackage, assinatura e SHA-256 em Windows | Fase 6 |
| R15 chave de produção de licenças | Gerar em ambiente controlado, embutir a pública, documentar o `kid` | Antes do release |
| R27 privilégios do banco comercial | Definir usuário de aplicação com privilégios mínimos no PostgreSQL | Antes de qualquer produção |
| R03 com provedor real | Revalidar webhook com o adaptador real (hoje só sandbox) | Quando houver provedor |

## 5. Limitações desta avaliação (o que não foi verificado)

- Nada foi executado em **Windows**: DPAPI, jpackage, instalador e assinatura estão sem verificação real. Em Linux só vale o protetor de desenvolvimento, que exige opt-in explícito.
- Sem teste de intrusão externo, sem varredura dinâmica (ZAP/Burp) e sem campanha de *fuzzing* além dos testes unitários de entradas malformadas.
- O limitador de taxa fica em memória: não protege contra ataque distribuído em várias instâncias.
- Tempo de resposta de login/recuperação **não foi medido** estatisticamente; a uniformidade verificada é a das respostas, não do tempo.
- O cache de licença no cliente é editável por quem controla a conta do Windows (risco aceito R23/R24).
- O provedor de pagamento real não foi integrado; a decisão é provisória porque os domínios dos provedores estavam inacessíveis.
- Não houve teste de carga.
