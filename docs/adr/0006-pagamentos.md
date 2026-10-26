# ADR-0006 — Pagamentos

Status: aceita; provedor real pendente (ver docs/payments/PROVIDER_DECISION.md)

**Decisão.** Interface `ProvedorPagamento`; provedor Sandbox com assinatura HMAC-SHA256 sobre `timestamp.corpo`; tabela de eventos processados para idempotência; máquina de estados da assinatura para ordem; `PAYMENTS_ENABLED=false` por padrão e produção exige configuração completa (falha fechada).
