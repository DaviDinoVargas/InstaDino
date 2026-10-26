# Registro de Riscos — InstaDino

Escala: Impacto (I) e Probabilidade (P) de 1 a 5. Pontuação = I × P. Classificação: ≥ 15 **crítico**, 10–14 **alto**, 5–9 **médio**, < 5 **baixo**.

Situação: **Aberto** (controle ainda não implementado), **Mitigado** (controle implementado e testado), **Aceito** (decisão formal com justificativa).

Esta é a linha de base da Avaliação 1. A situação é atualizada ao longo do projeto e confirmada nas Avaliações 2 e 3.

| Id | Risco | I | P | Pts | Classe | Controle / requisito | Situação |
|---|---|---|---|---|---|---|---|
| R01 | ZIP malicioso causa gravação fora da área controlada (path traversal) | 5 | 3 | 15 | Crítico | IMP-01, IMP-02 | Aberto |
| R02 | ZIP bomb esgota memória/disco | 4 | 4 | 16 | Crítico | IMP-03, IMP-07 | Aberto |
| R03 | Pagamento marcado como confirmado sem validação (webhook forjado/replay) | 5 | 3 | 15 | Crítico | PAG-02, PAG-03 | Aberto |
| R04 | Eventos fora de ordem concedem Premium indevido | 4 | 3 | 12 | Alto | PAG-04 | Aberto |
| R05 | Usuário habilita Premium alterando arquivo/flag local | 4 | 5 | 20 | Crítico | LIC-01, LIC-03 | Aberto |
| R06 | Acesso a licença/dados de outra conta (IDOR) | 5 | 3 | 15 | Crítico | AUZ-01 | Aberto |
| R07 | Escalonamento para papel MASTER | 5 | 2 | 10 | Alto | AUZ-02 | Aberto |
| R08 | Força bruta / credential stuffing no login | 4 | 4 | 16 | Crítico | AUT-01, AUT-03 | Aberto |
| R09 | Enumeração de contas | 3 | 4 | 12 | Alto | AUT-04 | Aberto |
| R10 | Banco SQLite lido por terceiros sem cifragem | 5 | 3 | 15 | Crítico | DB-01, DB-02, DB-03 | Aberto |
| R11 | Chave do banco fixa ou vazada | 5 | 2 | 10 | Alto | DB-02 | Aberto |
| R12 | SQL injection | 5 | 2 | 10 | Alto | DB-04 | Aberto |
| R13 | Segredo, senha ou ZIP real publicado no GitHub (repositório público) | 5 | 3 | 15 | Crítico | SUP-01, AUT-09 | Aberto |
| R14 | Dependência vulnerável ou comprometida | 4 | 3 | 12 | Alto | SUP-02, SUP-03 | Aberto |
| R15 | Chave privada de licenças vazada | 5 | 2 | 10 | Alto | LIC-02, rotação por `kid` | Aberto |
| R16 | Dados pessoais da exportação enviados ao backend por engano | 5 | 2 | 10 | Alto | PRI-01 | Aberto |
| R17 | Fórmula maliciosa em CSV executada em planilha (CSV injection) | 3 | 3 | 9 | Médio | PRI-04 | Aberto |
| R18 | Segredos/dados pessoais em logs | 4 | 3 | 12 | Alto | AUT-07, DB-09 | Aberto |
| R19 | Falha de migração corrompe banco local | 4 | 2 | 8 | Médio | DB-05 | Aberto |
| R20 | Importação interrompida corrompe histórico | 4 | 3 | 12 | Alto | IMP-06 | Aberto |
| R21 | Relógio adulterado estende licença offline | 3 | 4 | 12 | Alto | LIC-04 | Aberto |
| R22 | Cobrança real acidental por configuração incompleta | 5 | 2 | 10 | Alto | PAG-06, PAG-09 | Aberto |
| R23 | Engenharia reversa do desktop remove verificação de licença | 3 | 5 | 15 | Crítico | Ver decisão R23 abaixo | **Aceito (residual)** |
| R24 | Computador do usuário comprometido ou perfil compartilhado | 4 | 3 | 12 | Alto | Limites documentados; DPAPI | **Aceito (residual)** |
| R25 | Instalador adulterado/não assinado | 5 | 2 | 10 | Alto | SUP-04 | Aberto |
| R26 | Interpretação errada dos dados leva a acusação indevida (ex.: "deixou de seguir") | 3 | 4 | 12 | Alto | Avisos de limitação na UI e relatórios | Aberto |
| R27 | Operador com acesso excessivo ao banco comercial | 4 | 2 | 8 | Médio | Privilégios mínimos, auditoria | Aberto |
| R28 | Dependência de infraestrutura indisponível bloqueia acesso aos dados locais | 3 | 3 | 9 | Médio | LIC-05 | Aberto |

## Decisões formais de aceitação

**R23 — engenharia reversa do desktop.** O desktop roda em máquina controlada pelo usuário; nenhum controle local impede que um atacante determinado remova a verificação. Decisão: aceitar como risco residual. Mitigações proporcionais: licença assinada (a emissão depende do servidor), vínculo a dispositivo, assinatura de código do instalador, verificações de integridade que não comprometam estabilidade. O valor comercial protegido é limitado a recursos locais; contas, pagamentos e licenças são impostos no servidor.

**R24 — máquina comprometida/perfil compartilhado.** DPAPI protege contra leitura offline do disco e outros usuários do Windows, mas não contra código executando como o mesmo usuário nem contra administradores. Decisão: aceitar, documentar em `DATABASE_SECURITY.md` e na tela de Segurança e Privacidade.

## Riscos críticos: tratamento obrigatório antes da implementação dos fluxos sensíveis

Nenhum risco crítico é ignorado. Cada um tem requisito rastreável (coluna "Controle") e será revalidado na Avaliação 2. Os fluxos de importação, autenticação, licenciamento e pagamento só são implementados junto com os testes dos requisitos correspondentes.
