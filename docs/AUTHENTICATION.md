# Autenticação e Sessões

O backend comercial é a única fonte da verdade sobre contas. O desktop é um cliente não confiável: toda decisão de acesso é reavaliada no servidor. O plano **Free** nunca exige conta; a conta existe apenas para o Premium.

## Contas

| Aspecto | Implementação |
|---|---|
| Senha | Argon2id, sal de 16 bytes, hash de 32 bytes, memória 19 MiB, 2 iterações, paralelismo 1 (mínimo recomendado pela OWASP) |
| Política | 12 a 128 caracteres; rejeita senhas comuns e senhas que contenham o e-mail ou o nome do produto |
| E-mail | Normalizado; verificação por token de uso único (24 h) |
| Papéis | `CLIENTE` e `MASTER`; definidos **somente** no servidor (semeadura ou ação administrativa auditada). Nenhum campo do cliente é aceito como papel |

## Sessões

- Token opaco aleatório (≥ 256 bits). O servidor guarda apenas o **hash SHA-256**; o token em claro só existe no cliente.
- Validade padrão de 14 dias (`instadino.sessao.validade-em-dias`), com revogação individual (`DELETE /v1/sessoes/{id}`), da sessão atual e em massa (troca de senha, ação administrativa).
- No desktop o token e o cache da conta ficam protegidos por DPAPI no Windows.

## Proteção contra abuso

| Controle | Padrão |
|---|---|
| Limite de taxa por IP e rota (cadastro, verificação, login, recuperação, redefinição) | 30 requisições por janela de 5 minutos; resposta `429` com `Retry-After` |
| Bloqueio por conta | a partir de 5 falhas consecutivas, 15 min, **dobrando** a cada falha adicional (limite de 2⁶ vezes a base) |
| Resposta uniforme | login, cadastro e recuperação não revelam se o e-mail existe |
| Tokens de uso único | consumidos de forma atômica; redefinir a senha revoga as sessões |

## Endpoints

`POST /v1/contas`, `POST /v1/contas/verificar-email`, `POST /v1/sessoes` (login), `DELETE /v1/sessoes/atual`, `GET /v1/sessoes`, `DELETE /v1/sessoes/{id}`, `POST /v1/senha/recuperacao`, `POST /v1/senha/redefinir`, `GET /v1/conta`, `GET /v1/status`. Contrato completo em `docs/api/openapi.json`.

## Contas de demonstração (somente sandbox)

No perfil `desenvolvimento` o servidor cria três contas se ainda não existirem:

| E-mail | Perfil |
|---|---|
| `master@instadino.test` | `MASTER`, acesso total e endpoints administrativos |
| `premium@instadino.test` | cliente com concessão Premium (sem data final) |
| `free@instadino.test` | cliente sem direito Premium |

As senhas **não estão no repositório**. Vêm de `INSTADINO_SENHA_MASTER`, `INSTADINO_SENHA_PREMIUM` e `INSTADINO_SENHA_FREE`; se ausentes, são geradas aleatoriamente (20 caracteres) e gravadas em `contas-semeadas.txt` (permissão 0600, ignorado pelo Git). Nunca são registradas em log. A semeadura é desabilitada no perfil `producao`.

## Limites conhecidos

- O limitador de taxa fica em memória: com mais de uma instância do backend cada uma conta separadamente (ver `RISK_REGISTER.md`).
- O envio de e-mail de produção não está implementado; em desenvolvimento os tokens são gravados em arquivos locais para teste.
- Não há segundo fator de autenticação nesta versão.
