# Segurança da Importação

O ZIP é tratado como entrada hostil (requisitos IMP-01 a IMP-09). Status: implementado e testado em Linux (JDK 21) com ZIPs sintéticos maliciosos.

## Princípios

1. **Nada é extraído para o disco.** As entradas são lidas em memória, em streaming. Não existe caminho de destino derivado do ZIP, portanto não há como gravar fora de uma área controlada.
2. **Nada é executado.** O conteúdo é lido como dado: JSON por parser com limites; HTML por jsoup, só para localizar links.
3. **Os limites são aplicados sobre bytes reais.** Tamanhos declarados no ZIP podem mentir; a leitura é contada byte a byte.
4. **A inspeção é refeita no momento da leitura**, para que o arquivo analisado seja o confirmado (evita troca entre inspeção e leitura).
5. **Falhas não alteram o histórico**: a persistência só ocorre após a confirmação e em uma única transação.

## Limites (padrão, configuráveis em `LimitesDeImportacao`)

| Limite | Padrão |
|---|---|
| Tamanho do ZIP | 8 GiB |
| Entradas | 1.000.000 |
| Soma dos tamanhos descompactados declarados | 64 GiB |
| Bytes descompactados por entrada lida (JSON/HTML) | 256 MiB |
| Bytes descompactados lidos no total | 1 GiB |
| Profundidade de diretórios | 24 |
| Comprimento do caminho | 512 caracteres |
| Razão de compressão (entradas ≥ 1 MiB) | 200 : 1 |
| Tempo total | 10 minutos |

## Validação de caminhos

Rejeitados: vazio, `..`, caminho absoluto, unidade (`C:`), `\`, segmentos vazios, caracteres de controle e NUL, caracteres de formatação Unicode (por exemplo, inversão bidirecional que falsifica nomes), nomes longos demais, diretórios profundos demais. Duplicatas são detectadas após normalização Unicode (NFC) e sem distinção de maiúsculas.

## Cobertura de testes

`SegurancaDoImportadorTest` (28 testes) e `ImportadorDeExportacaoTest` (15). Cada cenário também verifica que **nenhum arquivo foi criado** além do ZIP de entrada. Os controles críticos foram submetidos a um teste de mutação manual (remoção do bloqueio de `..`, do limite real de bytes e da detecção de duplicatas): em todos os casos os testes passaram a falhar.

## Limites conhecidos

- A validação do ZIP pelo `java.util.zip` confia no diretório central; ZIPs com sobreposição de entradas não são especificamente detectados, mas só lemos arquivos JSON/HTML e com limite de bytes reais.
- Não foi feito *fuzzing* contínuo; é recomendável antes da release (Avaliação 3).
- ZIPs divididos em várias partes (a exportação pode gerar mais de um arquivo) devem ser importados separadamente; a junção não está implementada.
