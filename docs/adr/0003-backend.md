# ADR-0003 — Backend comercial

Status: aceita

**Decisão.** Spring Boot 3.5.x, PostgreSQL em produção (rede privada), H2 em testes, Flyway para migrações. Domínio e persistência como pacotes de `backend-api` nesta etapa.

**Consequências.** Testes do backend não exigem Docker. Testcontainers poderá ser adicionado para verificar PostgreSQL de forma opcional.
