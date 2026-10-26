# Build e empacotamento para Windows

## Requisitos

Windows 10/11 x64, JDK 21 (com `jpackage` e `jdeps`), Maven 3.9+. Para gerar MSI/EXE: WiX Toolset 3.x. Para assinar: certificado de assinatura de código (fora do repositório).

## Comandos

```powershell
mvn -B verify                                   # testes completos
scripts\empacotar-windows.ps1                   # app-image + ZIP + SHA256SUMS.txt (em dist\)
scripts\empacotar-windows.ps1 -Tipo msi         # instalador MSI por usuário (WiX)
scripts\empacotar-windows.ps1 -Tipo msi -Assinar  # exige INSTADINO_CERT_PFX e INSTADINO_CERT_SENHA no ambiente
scripts\testar-pacote-windows.ps1               # teste de fumaça do app empacotado
```

O pacote leva um runtime Java mínimo (montado com `jdeps`/`jlink` pelo `jpackage`), então o usuário **não precisa instalar Java**. `dist/` é ignorado pelo Git.

## O que o CI do GitHub (workflow “Windows”) verifica de fato

Em `windows-latest`, a cada push:

1. `mvn verify` completo — inclui o teste real da DPAPI e os testes de interface;
2. geração do app-image e do ZIP com SHA-256;
3. **teste de fumaça**: o aplicativo empacotado inicia, permanece vivo, cria o banco **cifrado** (sem o cabeçalho `SQLite format 3`) e a chave com a assinatura de blob da **DPAPI** (`01 00 00 00 D0 8C 9D DF`);
4. job do instalador: gera o MSI, **instala em silêncio**, executa o app instalado, **desinstala** e confere que o executável sumiu e que o banco e a chave do usuário **permaneceram**.

Os artefatos publicados chamam-se `…-NAO-ASSINADO`.

## O que NÃO está verificado

- **Assinatura de código**: o caminho `-Assinar` (signtool) existe, mas **nunca foi executado** — não há certificado. Sem assinatura o Windows mostra aviso de editor desconhecido/SmartScreen. Não distribua como versão final.
- Instalação em máquina limpa de usuário (Windows 10 e 11 reais, antivírus, contas sem privilégio). O CI usa uma imagem de servidor do GitHub.
- **Atualização sobre versão anterior** (o MSI usa um `--win-upgrade-uuid` fixo para permitir upgrade, mas isso não foi testado) e instalação para todos os usuários.
- Ícone próprio do aplicativo (ainda não há arte final; usa o ícone padrão do `jpackage`).
- Desempenho e comportamento com exportações reais grandes.

## Instalação, atualização e desinstalação

- Instalação **por usuário** em `%LOCALAPPDATA%\InstaDino` (não exige administrador).
- Dados do usuário em `%LOCALAPPDATA%\InstaDinoDados` (banco cifrado, chave protegida por DPAPI, cache da conta). **Ficam fora da pasta do programa**, então desinstalar não apaga os dados (verificado no CI). Para remover tudo use “Excluir todos os dados locais” na tela de Segurança do aplicativo antes de desinstalar.
- **Não há atualização automática.** Um atualizador automático é um canal de distribuição de código e só será adicionado junto com um canal assinado e verificável. Por ora, baixe o novo instalador e instale por cima.

## Segredos de assinatura

`INSTADINO_CERT_PFX` (caminho do `.pfx`) e `INSTADINO_CERT_SENHA` existem **apenas** no ambiente do processo de release. Nunca são lidos de arquivos do repositório nem registrados em log.
