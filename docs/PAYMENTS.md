# Pagamentos

> **Estado: SOMENTE SANDBOX.** Nenhuma cobrança real é possível nesta versão. Não existe adaptador de provedor real; a decisão do provedor é provisória (ver `payments/PROVIDER_DECISION.md`).

## Princípios

1. O cliente **nunca** confirma pagamento. Não existe endpoint que aceite essa declaração (PAG-01).
2. Preço e plano vêm do catálogo do servidor; valores enviados pelo cliente são ignorados (PAG-05).
3. Direitos só mudam por evento autenticado do provedor, processado por um único caminho: `ProcessadorDeEventosDePagamento`.
4. Falha fechada: `PAYMENTS_ENABLED=false` por padrão; o modo de produção está desabilitado e `ProvedorNaoImplementado` recusa tudo.

## Fluxo (sandbox)

`GET /v1/planos` → `POST /v1/checkout` (cria pedido com o preço do servidor e devolve URL de checkout `.invalid`) → eventos simulados em `POST /v1/sandbox/pedidos/{id}/eventos/{tipo}` (exige login e modo sandbox) **ou** entrega assinada em `POST /v1/webhooks/pagamento`. As duas vias passam pelo **mesmo** processador.

## Webhook

- Cabeçalho `X-Sandbox-Signature: t=<epoch>,v1=<hex>`; HMAC-SHA256 sobre `t + "." + corpo bruto`, comparação em tempo constante, janela de 5 minutos.
- Idempotência: o id do evento é a chave primária de `evento_pagamento`. A inserção usa `INSERT` (a entidade é `Persistable`); a entrega que perde a corrida recebe violação de chave e responde `DUPLICADO`.
- Ordem: evento com horário do provedor anterior ao último aplicado à assinatura é `IGNORADO_FORA_DE_ORDEM`.
- Valor e moeda precisam coincidir com o pedido; divergência é ignorada e auditada.
- Tipos tratados: aprovado, renovação, recusado, cancelamento, reembolso, disputa. Reembolso e disputa **revogam** assinatura e licenças.

## Dados

Nenhum dado de cartão trafega ou é armazenado (PAG-07). O plano de teste custa 1990 centavos e está marcado como **preço de teste**.

## Para ativar cobrança real (não feito)

Siga a lista em `payments/PROVIDER_DECISION.md` §4. Em resumo: confirmar a documentação oficial do provedor, implementar o adaptador atrás de `ProvedorPagamento`, testar em modo de teste do provedor, revisar o webhook e registrar a aprovação explícita antes de mudar `PAYMENTS_MODE`.

## Executar localmente

`scripts/executar-sandbox.sh` (ou `.ps1` no Windows) compila, sobe o backend em `127.0.0.1:8443` com pagamentos em sandbox e abre o desktop apontando para ele.
