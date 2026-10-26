# ADR-0005 — Licença assinada

Status: aceita

**Decisão.** Documento JSON canônico com assinatura Ed25519 (JDK 21). Campos: produto, plano, titular, dispositivo, emissão, validade, tolerância offline, `kid`. O cliente embute apenas chaves públicas, com suporte a mais de uma por `kid`.

**Consequências.** Rotação de chaves sem atualizar o cliente imediatamente. Proteção contra engenharia reversa é limitada e declarada em RISK_REGISTER (R23).
