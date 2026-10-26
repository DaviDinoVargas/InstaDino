# ADR-0007 — Bibliotecas de formato

Status: aceita

**Decisão.** Jackson (JSON) com limites de leitura, jsoup (HTML como texto), Apache Commons CSV (com neutralização de fórmulas), PDFBox/OpenHTMLtoPDF para relatórios, JFreeChart apenas se os gráficos nativos do JavaFX forem insuficientes.

**Consequências.** Cada dependência é registrada em `docs/THIRD_PARTY.md` quando introduzida.
