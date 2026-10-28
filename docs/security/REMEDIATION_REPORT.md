# Relatório de Remediação

Estado em 2026-10-28. Lista cada achado das Avaliações 2 e 3, a correção e a evidência. "Corrigido" significa corrigido **e** coberto por teste ou verificação automatizada que falha se o problema voltar. Os achados originais estão em `SECURITY_ASSESSMENT_2.md` e `FINAL_SECURITY_AUDIT.md`.

## Avaliação 2

| Id | Sev. | Correção | Evidência | Estado |
|---|---|---|---|---|
| V-01 | Crítica | Chave do banco passada pelos parâmetros de URL do driver | Teste lê o arquivo em disco e não encontra o cabeçalho `SQLite format 3`; confirmado de novo no Windows pelo teste de fumaça do pacote | Corrigido |
| V-02 | Alta | Idempotência do webhook por chave primária do evento (`Persistable`) | Teste determinístico de entrega concorrente | Corrigido |
| V-03 a V-09 | Média a Info | Ver `SECURITY_ASSESSMENT_2.md` | Testes de regressão correspondentes | Corrigidos |
| V-10 | Baixa | Testes de arquitetura de privacidade | `ArquiteturaDePrivacidadeTest`, `ArquiteturaDePrivacidadeServidorTest` | Corrigido |
| V-11 | Alta | Auditoria de dependências executada na CI (OSV) | Job "Auditoria de vulnerabilidades das dependências (OSV)" verde; ver A3-01 e A3-02 | **Fechado pela Avaliação 3**, com duas exceções datadas |

## Avaliação 3

| Id | Sev. | Correção | Evidência | Estado |
|---|---|---|---|---|
| A3-01 | Alta | Jackson 2.22.4, Tomcat 10.1.59, Log4j 2.25.5, commons-lang3 3.18.0, PostgreSQL 42.7.12 no backend | OSV verde; `mvn dependency:tree` mostra as versões; 104 testes do backend passam | Corrigido |
| A3-02 | Média | Exceção temporária para spring-webmvc 6.2.19 | `config/osv-excecoes.json` (motivo e data); `scripts/resumir-osv.py` falha se a exceção vencer ou se surgir aviso novo | **Aceito até 2026-12-15** |
| A3-03 | Alta | Dados do usuário fora da pasta de instalação | Job do MSI confere que banco e chave sobrevivem à desinstalação; `DiretorioDeDadosTest` | Corrigido |
| A3-04 | Média | Nulidade capturada logo após cada leitura | Teste de regressão de datas ausentes | Corrigido |
| A3-05 | Média | `FontMapper` próprio; cronômetro monotônico | Testes de PDF e de tempo máximo verdes no Windows | Corrigido |
| A3-06 | Baixa | Boxing removido | SpotBugs sem achados | Corrigido |
| A3-07 | Info | Testes de interface esperam o texto em vez de lê-lo uma vez | 11 rodadas seguidas sem falha | Corrigido |

## Pendências que não são de código

| Pendência | Quem resolve | Por que está aberta |
|---|---|---|
| Certificado de assinatura de código e assinatura do instalador (R25) | Responsável pelo produto | Não há certificado; o caminho `-Assinar` nunca foi executado |
| Chave de produção de licenças (R15) | Responsável pelo produto, em ambiente controlado | A chave privada nunca deve passar por este ambiente |
| Teste manual em Windows 10 e 11 limpos, incluindo atualização do MSI | Responsável pelo produto | Exige máquinas físicas ou virtuais limpas |
| Revisão jurídica de licenças, termos e privacidade | Responsável pelo produto | Fora do escopo técnico |
| Provedor de pagamento real | Responsável pelo produto | Decisão comercial; produção está desabilitada e falha fechada |
| Privilégios mínimos do banco comercial (R27) | Operador | Não há ambiente de produção |
| Migração para Spring Boot 4 / Spring 7 (fim da exceção A3-02) | Equipe | Mudança grande de versão principal, fora do escopo desta fase |
