# Avaliação de Segurança 3 — Pré-lançamento

Data: 2026-10-28 · Escopo: tudo o que existe no repositório (importador, banco local cifrado, backup protegido, atividade, conteúdo, relatórios PDF, módulo de mensagens, backend comercial em **sandbox**, licenciamento, empacotamento Windows e a integração contínua). Base: ramo `funcionalidade/sbom-e-analise-estatica`, a partir de `desenvolvimento`.

> Não é um teste de intrusão independente nem uma certificação. É uma revisão do próprio time, apoiada em testes automatizados, análise estática, auditoria de dependências e execução em Windows na CI. As limitações estão na seção 6. **Esta avaliação não libera um lançamento**: ela diz o que está comprovado e o que impede o lançamento (seção 7).

## 1. Método e evidências

| # | Evidência | Resultado |
|---|---|---|
| 1 | Suíte completa (`mvn verify`, interface sob `xvfb-run`) | **0 falhas, 0 erros, 1 ignorado** (o teste da DPAPI, que só roda em Windows). Contagem por módulo em `docs/TEST_STRATEGY.md` |
| 2 | Execução em **Windows** na CI (`windows.yml`): suíte completa incluindo o teste real da DPAPI, `jpackage`, teste de fumaça do app empacotado, MSI instalar → executar → desinstalar | Verde. O banco aparece cifrado e a chave com a assinatura de blob da DPAPI; os dados do usuário sobrevivem à desinstalação |
| 3 | **Análise estática** (SpotBugs 4.9.8.2, esforço máximo, limiar médio), executada em todos os módulos dentro do `verify` | 0 achados após as exclusões justificadas em `config/spotbugs-exclusoes.xml`. Antes das exclusões: 87 avisos, dos quais 73 de exposição de representação interna (sem fronteira de confiança), 10 de `Path.getParent()` já absoluto, 1 SQL não constante validado, 1 construtor que falha de propósito, 1 `SecureRandom` e **1 boxing real, corrigido** |
| 4 | **SBOM** CycloneDX 1.6 (`scripts/gerar-sbom.sh`), publicado como artefato da CI | 114 componentes |
| 5 | **Auditoria de vulnerabilidades das dependências** com OSV a partir do SBOM, na CI (o ambiente de desenvolvimento não alcança OSV, NVD nem a API de advisories) | Achou vulnerabilidades reais (A3-01). Corrigidas ou registradas como exceção datada. Job verde |
| 6 | Varredura de segredos no índice **e** no histórico (`scripts/verificar-segredos.sh --historico`), na CI e local | Nada encontrado |
| 7 | Verificação de rede: nenhum código fora de `licensing-client` e `backend-api` usa HTTP, sockets ou URLs; nenhum uso de `ProcessBuilder` ou `Runtime.exec` | Confirmado por busca no código |
| 8 | Logs do importador, dos serviços e do desktop | Só nomes de classe e tipos de evento; nenhum nome de usuário, texto ou caminho de exportação |
| 9 | Teste de varredura do banco: importa conversas com texto, nome de mídia, reação e link marcados e lê **todas as colunas de todas as tabelas** | Nenhum dado marcado chegou ao banco; os agregados estão lá |
| 10 | Mutação manual dos controles do módulo de mensagens (seção 4) | Os 3 mutantes foram detectados |

## 2. Achados desta avaliação

| Id | Sev. | Achado | Como foi encontrado | Correção | Estado |
|---|---|---|---|---|---|
| A3-01 | **Alta** | Dependências do backend com vulnerabilidades conhecidas: Jackson 2.21.4 (databind e core, ao menos 10 avisos), Tomcat embutido 10.1.55 (4), Log4j API 2.24.3 (1), commons-lang3 3.17.0 (1), PostgreSQL JDBC 42.7.11 (1), spring-webmvc 6.2.19 (2, ambos CVSS 9.8) | Auditoria OSV na CI (primeira execução real desta auditoria, V-11 da Avaliação 2) | Jackson 2.22.4 (BOM do Jackson importado antes do do Spring Boot), Tomcat 10.1.59, Log4j 2.25.5, commons-lang3 3.18.0, PostgreSQL 42.7.12. O desktop nunca usou as versões afetadas | Corrigido, **exceto** spring-webmvc (A3-02) |
| A3-02 | Média | spring-webmvc 6.2.19: GHSA-j9f9-w8pj-32f8 (SSE com fragmentos de visão) e GHSA-pc63-qcmh-9cmg (travessia de caminho em `XsltView`). Não existe versão corrigida na série 6.2; a correção está no Spring Framework 7.0.9 | Mesma auditoria | Ambos exigem renderização de visões; o backend só tem 5 `@RestController`, sem visões, sem SSE e sem fragmentos (verificado por busca). **Exceção temporária** com motivo e data (`config/osv-excecoes.json`, revisar até 2026-12-15); vencida, o job volta a falhar | **Aceito (temporário)**; sai quando o backend migrar para Spring Boot 4 / Spring 7 |
| A3-03 | **Alta** (perda de dados) | No Windows a pasta de dados era a pasta de instalação: desinstalar apagaria o banco do usuário | Job do MSI na CI (desinstalar e conferir que os dados permanecem) | Dados em `%LOCALAPPDATA%\InstaDinoDados`, separados do programa; teste no CI e `DiretorioDeDadosTest` | Corrigido |
| A3-04 | Média (integridade) | Colunas nulas lidas como 0 (`wasNull()` só vale para a última coluna lida): datas ausentes apareciam como 1970 e contagens opcionais saíam erradas | Teste de ida e volta com datas ausentes | Captura de nulidade logo após cada leitura em todos os repositórios; teste de regressão | Corrigido |
| A3-05 | Média (disponibilidade) | Geração de PDF levava 91 s no Windows porque a biblioteca varria as fontes do sistema; e um teste de tempo máximo usava o relógio de parede | Job do Windows na CI | `FontMapper` próprio (Helvetica/WinAnsi, sem fontes do sistema) e cronômetro monotônico | Corrigido |
| A3-06 | Baixa | Boxing/unboxing desnecessário no importador | SpotBugs | Corrigido | Corrigido |
| A3-07 | Info (qualidade dos testes) | Teste de interface instável: `InterfaceTest.fluxoCompletoDeImportacaoPelaInterface` falhou em 2 de ~12 rodadas locais e uma vez na CI Linux. Causa: a asserção de texto lia a tabela do inventário logo após o botão aparecer, antes de o próximo layout criar as células. Era defeito do teste, não do produto | Repetição em laço, lendo a linha da falha | As 33 asserções do mesmo padrão nos testes de interface passaram a esperar o texto (com tempo limite); 11 rodadas seguidas sem falha depois da correção | Corrigido |

Nenhum achado crítico foi encontrado nesta avaliação. Nenhum achado alto está aberto.

## 3. Situação dos requisitos desde a Avaliação 2

- **Passam a atendidos:** DB-03 (DPAPI verificada em Windows real **na CI**, não em máquina limpa de usuário), SUP-02 (auditoria de dependências, na CI), SUP-03 (SBOM), PRI-02 (consentimento do módulo de mensagens: Premium + consentimento explícito, desligado por padrão, revogável com apagamento; texto nunca chega ao banco, comprovado por varredura de todas as tabelas).
- **Continuam parciais:** DB-09 e AUT-07 (logs verificados por busca no código e nos fluxos testados, sem teste que force todos os caminhos de erro), PAG-09 (sem adaptador real a revisar).
- **Continuam não atendidos:** SUP-04 (instalador **assinado**: não há certificado; o caminho `-Assinar` nunca foi executado).
- AUT-09, SUP-01, SUP-05: atendidos (varreduras limpas).

## 4. Verificações específicas das funções novas

**Mensagens (único módulo sensível processado).** Controles verificados por teste e por mutação manual: (a) leitura exige plano Premium e consentimento (mutante A: remover o `exigir` da leitura → 3 testes falham); (b) a importação ignora mensagens sem consentimento e sem Premium, em duas camadas (mutante B: remover só o filtro da configuração → a segunda camada, a verificação na gravação, derruba 2 testes; mutante C: remover as duas camadas → 2 testes falham por terem gravado dados sem consentimento); (c) revogar apaga tudo e funciona mesmo sem Premium; (d) nenhum texto, nome de mídia, reação ou link chega ao banco (varredura de todas as colunas); (e) o diagnóstico mostra uma linha agregada e nunca nomes de pastas, que contêm nomes de usuário; (f) o titular não é adivinhado: com uma só conversa as contagens de "enviadas" ficam indisponíveis.

**Backup protegido por senha.** PBKDF2-HMAC-SHA256 com 600 000 iterações e sal individual; o cabeçalho é validado (limites de iterações) antes de qualquer derivação; a restauração verifica integridade e cifragem antes de substituir o banco e mantém o banco anterior. Senha mínima de 12 caracteres e zerada em memória.

**PDF.** Sem JavaScript, anexos nem ações; só fontes padrão; por padrão só números (listas de contas, etiquetas e notas exigem opção explícita).

**Importador.** Atividade, conteúdo, anúncios e mensagens usam o mesmo percurso em streaming com limites de profundidade, de tamanho de texto e de bytes lidos; HTML dessas categorias não é lido.

## 5. Dados de terceiros

As conversas e o catálogo de mídia contêm nomes de usuário e títulos de outras pessoas. O produto guarda só os mínimos para as estatísticas, localmente e cifrados; nunca os envia (verificado pelos testes de arquitetura de privacidade) nem os põe em relatórios por padrão. Os contatos sincronizados **não foram implementados** justamente por serem o dado de terceiros de maior risco.

## 6. Limitações desta avaliação (o que não foi verificado)

- **Sem exportação real** para atividade, conteúdo, anúncios e mensagens: os leitores foram validados só com arquivos sintéticos. Só os relacionamentos foram conferidos com a exportação do responsável do produto. Um formato real diferente pode produzir leituras incompletas (o diagnóstico por arquivo existe para detectá-las), não falhas de segurança.
- Nenhuma execução em **Windows limpo de usuário** (Windows 10 e 11 reais, antivírus, conta sem privilégio) e nenhum teste de **atualização** do MSI.
- Sem teste de intrusão externo, sem varredura dinâmica e sem *fuzzing* contínuo.
- A auditoria OSV só enxerga o que o OSV conhece, só roda com rede e a cada execução da CI; vulnerabilidades novas surgem depois da data desta avaliação.
- O limitador de taxa do backend fica em memória; a uniformidade de tempo de resposta do login não foi medida estatisticamente; não houve teste de carga.
- Nenhum provedor de pagamento real foi integrado e nenhuma cobrança real é possível: produção falha fechada.
- O cache de licença no cliente é editável por quem controla a conta do Windows (R23/R24 aceitos).

## 7. Parecer

Não há achado crítico ou alto aberto **no código e na configuração**. O lançamento de um instalador para usuários **continua bloqueado** por itens que dependem do responsável pelo produto ou de ambiente que não existe aqui, listados em `RELEASE_SECURITY_CHECKLIST.md`: certificado de assinatura de código, chave de produção de licenças gerada em ambiente controlado, teste manual em Windows limpo, revisão jurídica e, se houver cobrança, provedor de pagamento escolhido e revisado com aprovação explícita. Pagamentos reais seguem desabilitados.
