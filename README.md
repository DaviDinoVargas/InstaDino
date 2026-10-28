# InstaDino

Aplicativo desktop para Windows, escrito em Java, que analisa **localmente** os dados da exportação oficial do Instagram fornecida pelo próprio usuário.

> **Estado do projeto: em desenvolvimento, ainda não é uma versão comercial.** Este README descreve o que existe e foi testado. O detalhamento de cada área está em `docs/`.

## O que já funciona

| Área | Estado |
|---|---|
| Importação segura do ZIP oficial (JSON e HTML), inventário de categorias, limites anti-ZIP-bomb, proteção contra path traversal | **Implementado e testado** (com exportações sintéticas) |
| Seguidores, seguindo, mútuos, não recíprocos, painel social, busca, ordenação, paginação | **Implementado e testado** |
| Banco SQLite **cifrado**, chave protegida por DPAPI (Windows), migrações versionadas, backup, exclusão | **Implementado e testado em Linux**; DPAPI e Windows ainda **não verificados** em Windows real |
| Histórico, comparação entre importações, CSV (básico e personalizado), política Free/Premium | **Implementado e testado** |
| Interface JavaFX (tema escuro/claro, navegação, importação guiada, tabelas paginadas) | **Implementado**, testado sob display virtual |
| Backend comercial (contas, sessões, licença assinada Ed25519, auditoria), tela de Conta/Premium no desktop | **Implementado e testado** (ponta a ponta local; ver `docs/AUTHENTICATION.md` e `docs/LICENSING.md`) |
| Pagamentos | **Somente sandbox.** Sem provedor real e sem cobrança possível; produção desabilitada (`docs/PAYMENTS.md`) |
| Gráficos históricos, relatórios PDF (resumo, comparativo, qualidade), etiquetas, favoritos, notas, múltiplos perfis, backup protegido por senha | **Implementado e testado** (Premium) |
| Biblioteca de conteúdo, análise de atividade (curtidas, comentários, salvos, pesquisas, visualizações), anúncios e interesses, explorador de dados, painéis personalizados | **Implementado e testado com dados sintéticos**; os formatos de atividade, conteúdo e anúncios **ainda não foram conferidos com uma exportação real** |
| Estatísticas de mensagens (somente contagens e datas, nunca o texto; Premium + consentimento explícito, revogável) | **Implementado e testado com dados sintéticos** |
| Perfil e segurança da conta, contatos sincronizados, galeria de mídia, busca global, relatório "retrospectiva" | **Não implementados** (ver `docs/ROADMAP.md`) |
| Execução em Windows (testes, DPAPI, empacotamento `jpackage`, instalador MSI instalar/executar/desinstalar) | **Verificado na CI do GitHub (Windows)**; ainda sem teste manual em Windows limpo e **sem assinatura de código** |

Avaliações de segurança: a **1** (modelo de ameaças) e a **2** (`docs/security/SECURITY_ASSESSMENT_2.md`) estão em `docs/security/`; a **3** (pré-lançamento) está em `docs/security/FINAL_SECURITY_AUDIT.md`. A Avaliação 2 deixa pendências abertas, entre elas a auditoria de vulnerabilidades de dependências.

## Princípios

- Os dados da exportação nunca saem do computador do usuário.
- O plano Free é permanente e não exige cadastro.
- Pagamentos reais permanecem **desabilitados** até validação explícita.
- O programa não inventa dados nem afirma eventos que a exportação não comprova (por exemplo, "deixou de seguir").

## Requisitos para desenvolver

- JDK 21 (LTS) e Maven 3.9+.
- Para os testes de interface: um display (Linux: `xvfb-run`). Sem display, esses testes são **ignorados explicitamente**, nunca aprovados por omissão.

## Compilar e testar

```bash
mvn -B verify                 # sem display: os testes de interface são ignorados
xvfb-run -a mvn -B verify     # com display virtual: roda tudo
scripts/verificar-segredos.sh --historico
```

## Executar (desenvolvimento)

```bash
# Linux/macOS (sem DPAPI): exige opt-in explícito, e o aplicativo avisa que NÃO há proteção real da chave.
mvn -B -pl desktop-app -am install -DskipTests
JAVA_TOOL_OPTIONS="-Dinstadino.modoInseguroDesenvolvimento=true" mvn -B -pl desktop-app javafx:run
# Opcional: INSTADINO_DADOS=/caminho/da/pasta define onde ficam banco e chave.
```

Sem esse opt-in, fora do Windows, o aplicativo recusa criar o banco e mostra uma tela explicando o motivo.

No Windows a chave do banco é protegida pela DPAPI e nenhum opt-in é necessário.

## Executar com o backend local (sandbox)

```bash
scripts/executar-sandbox.sh      # Windows: scripts\executar-sandbox.ps1
```

Compila, sobe o backend em `127.0.0.1:8443` com pagamentos em sandbox e abre o desktop apontando para ele. No perfil de desenvolvimento são criadas três contas de demonstração (master, premium, free). As senhas **não estão no repositório**: defina `INSTADINO_SENHA_MASTER`, `INSTADINO_SENHA_PREMIUM` e `INSTADINO_SENHA_FREE` antes da primeira execução, ou consulte o arquivo `contas-semeadas.txt` gerado localmente (ignorado pelo Git). Para trocar as senhas por novas e aleatórias, use `--redefinir-senhas` (`-RedefinirSenhas` no PowerShell). Detalhes em `docs/AUTHENTICATION.md`.

## Estrutura

Ver `docs/ARCHITECTURE.md`. Resumo: `application-core` (domínio e política de acesso), `payment-contracts`, `instagram-importer`, `analytics-engine`, `persistence`, `licensing-client`, `backend-api`, `application-services` (casos de uso), `desktop-app` (JavaFX), `shared-test-fixtures` (dados sintéticos). Empacotamento e lançamento: `docs/WINDOWS_BUILD.md` e `docs/RELEASE_PROCESS.md`.

## Licença

Todos os direitos reservados, até que o responsável pelo produto defina outra licença. Dependências de terceiros mantêm suas licenças originais.
