# ADR-0004 — Autenticação

Status: aceita

**Decisão.** Argon2id (Spring Security `Argon2PasswordEncoder`), sessões com token opaco de 256 bits (somente o hash SHA-256 é persistido), expiração e revogação; recuperação com token de uso único. Sem JWT, para permitir revogação imediata.

**Consequências.** Uma consulta ao banco por requisição autenticada; aceitável para o volume esperado.
