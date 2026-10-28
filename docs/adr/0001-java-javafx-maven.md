# ADR-0001 — Java 21, JavaFX 25 e Maven multimódulo

Status: aceita

**Contexto.** O produto é um aplicativo desktop Windows em Java com interface JavaFX.

**Decisão.** Java 21 LTS (runtime disponível no ambiente: OpenJDK 21), JavaFX 21.0.13 (linha LTS 21, compatível com JDK 21), Maven com POM raiz que fixa versões em `dependencyManagement`.

**Consequências.** `jpackage` e `jlink` do JDK 21 produzem o runtime embutido; o instalador Windows só pode ser gerado em Windows.

**Correção (2026-10-26).** A primeira versão desta decisão previa JavaFX 25. Ao executar a interface, verificou-se que o JavaFX 25 é compilado para Java 23 ou superior (classes de versão 67) e **não funciona no JDK 21**. Como o requisito é Java 21 LTS, adotou-se o JavaFX 21.0.13. A migração para JavaFX 25 só deve ocorrer junto com a migração do projeto para um JDK 25 LTS.
