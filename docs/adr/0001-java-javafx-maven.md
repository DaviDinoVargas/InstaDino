# ADR-0001 — Java 21, JavaFX 25 e Maven multimódulo

Status: aceita

**Contexto.** O produto é um aplicativo desktop Windows em Java com interface JavaFX.

**Decisão.** Java 21 LTS (runtime disponível no ambiente: OpenJDK 21), JavaFX 25.0.x (linha LTS disponível no Maven Central em 2026-10-26), Maven com POM raiz que fixa versões em `dependencyManagement`.

**Consequências.** `jpackage` e `jlink` do JDK 21 produzem o runtime embutido; o instalador Windows só pode ser gerado em Windows.
