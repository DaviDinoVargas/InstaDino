# Decisão do Provedor de Pagamento

Status: **PROVISÓRIA — pendente de confirmação nas páginas oficiais e de avaliação comercial.**
Data: 2026-10-26

## 1. Limitação da pesquisa

Durante esta avaliação, os domínios `docs.stripe.com`, `stripe.com` e `mercadopago.com.br` **não estavam acessíveis** a partir do ambiente de desenvolvimento (bloqueio de rede). As informações abaixo vêm de resultados de busca na web, que citam trechos das documentações oficiais, e **não** substituem a leitura direta das páginas. Cada item marcado com ⚠ deve ser confirmado antes de qualquer ativação em produção.

## 2. Comparação

| Critério | Stripe | Mercado Pago |
|---|---|---|
| Checkout hospedado em modo assinatura | Citado na documentação (Checkout com modo de assinatura) ⚠ | A confirmar (assinaturas via "preapproval") ⚠ |
| Assinaturas recorrentes em BRL | Pix e boleto listados como suportando recorrência para clientes no Brasil, segundo a busca; a mesma busca registra uma divergência sobre o Pix Automático (convite) ⚠ | A confirmar ⚠ |
| Cartão em assinatura no Brasil | Não confirmado pela pesquisa ⚠ | Não confirmado ⚠ |
| Autenticação de webhook | Cabeçalho `Stripe-Signature` com `t` (timestamp) e `v1` (HMAC-SHA256 de `t.corpo_bruto`), segundo descrição de terceiros e SDK Java ⚠ | Cabeçalho `x-signature` com `ts` e `v1` (HMAC-SHA256), segundo a documentação citada na busca; o *template* da mensagem assinada inclui `data.id` e `x-request-id` ⚠ |
| Ambiente de testes | Modo de teste (a confirmar detalhes) ⚠ | URL de teste + credenciais de teste; criação de usuários de teste a confirmar ⚠ |
| SDK Java oficial | Sim (`com.stripe:stripe-java`) ⚠ | Sim (a confirmar versão/manutenção) ⚠ |
| Reembolso / disputa | Boleto: reembolsos e disputas **não** suportados, segundo a busca ⚠ | A confirmar ⚠ |

## 3. Decisão provisória

1. **Abstração própria** `ProvedorPagamento` no módulo `payment-contracts`. O núcleo comercial depende apenas dela.
2. **Provedor-alvo inicial: Stripe**, por oferecer Checkout hospedado, esquema de webhook com timestamp e identificador de evento (facilita janela de tempo e idempotência) e SDK Java. **Mercado Pago permanece como alternativa** caso a análise comercial (KYC, taxas, meios de pagamento exigidos pelo público) o favoreça.
3. **Implementação nesta fase:** um provedor `Sandbox` que gera eventos no **mesmo formato de assinatura** (HMAC-SHA256 sobre `timestamp.corpo`), permitindo testar verificação, replay, ordem e idempotência **sem cobrança real e sem inventar APIs**. O adaptador real só será escrito após a confirmação das ⚠ na documentação oficial e com credenciais de teste do provedor.
4. **Nenhum adaptador de produção é ativado.** `PAYMENTS_ENABLED=false` por padrão.

## 4. Pendências para ativar cobrança real

- [ ] Ler as páginas oficiais (Checkout/assinaturas, webhooks, eventos, reembolsos, testes) e atualizar este documento.
- [ ] Confirmar meios de pagamento recorrentes aplicáveis ao Brasil e custos.
- [ ] Definir preços, periodicidade, política de cancelamento e reembolso (CDC).
- [ ] Criar conta comercial, credenciais de teste e URL pública de webhook em TLS.
- [ ] Contratos, termos de uso, política de privacidade e dados fiscais (CNPJ/MEI) — decisão do responsável pelo produto.
- [ ] Testes de integração em modo de teste do provedor e revisão de segurança dos webhooks.
- [ ] Aprovação explícita registrada em `docs/RELEASE_PROCESS.md` antes de alterar `PAYMENTS_MODE`.
