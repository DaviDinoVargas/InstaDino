<#
.SYNOPSIS
  Gera o aplicativo InstaDino para Windows com jpackage (app-image, ZIP e, se o WiX estiver instalado, instalador EXE).
.DESCRIPTION
  Requisitos: Windows 10/11 x64, JDK 21 (jpackage e jdeps no PATH), Maven 3.9+.
  O instalador (-Tipo exe) exige o WiX Toolset 3.x (candle.exe/light.exe no PATH).
  A assinatura de código é OPCIONAL e usa SOMENTE variáveis de ambiente (veja docs/WINDOWS_BUILD.md).
  Nenhum certificado, senha ou token faz parte do repositório.
.PARAMETER Tipo
  app-image (padrão) ou exe.
.PARAMETER Versao
  Versão numérica (ex.: 0.1.0). Padrão: versão do POM sem -SNAPSHOT.
.PARAMETER Assinar
  Assina o EXE/instalador com signtool. Exige INSTADINO_CERT_PFX e INSTADINO_CERT_SENHA.
#>
[CmdletBinding()]
param(
    [ValidateSet('app-image', 'exe')][string]$Tipo = 'app-image',
    [string]$Versao,
    [switch]$Assinar
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

function Executar([string]$comando, [string[]]$argumentos) {
    & $comando @argumentos
    if ($LASTEXITCODE -ne 0) { throw "Falha em: $comando (código $LASTEXITCODE)" }
}

if (-not $Versao) {
    $pom = (& mvn -B -q help:evaluate '-Dexpression=project.version' -DforceStdout 2>$null) | Select-Object -Last 1
    $Versao = ($pom -replace '-SNAPSHOT', '').Trim()
    if (-not $Versao) { $Versao = '0.1.0' }
}
if ($Versao -notmatch '^\d+\.\d+\.\d+$') { throw "Versão inválida para o Windows: '$Versao' (use X.Y.Z)" }

Executar mvn @('-B', '-ntp', '-DskipTests', '-Pempacotamento', '-pl', 'desktop-app', '-am', 'package')
$entrada = Join-Path $raiz 'desktop-app\target\entrada-jpackage'
$principal = Get-ChildItem (Join-Path $raiz 'desktop-app\target') -Filter 'desktop-app-*.jar' |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
if (-not $principal) { throw 'JAR do aplicativo não encontrado.' }
Copy-Item $principal.FullName $entrada -Force

$modulos = (& jdeps --ignore-missing-deps --multi-release 21 --print-module-deps --class-path "$entrada\*" $principal.FullName) | Select-Object -Last 1
if ($LASTEXITCODE -ne 0 -or -not $modulos) { throw 'jdeps falhou.' }
# jdk.unsupported: exigido pelo JavaFX; jdk.crypto.ec e java.net.http: TLS e cliente da API; jdk.localedata: formatos pt-BR.
$modulos = (($modulos -split ',') + 'jdk.unsupported', 'jdk.crypto.ec', 'java.net.http', 'jdk.localedata' | Sort-Object -Unique) -join ','
Write-Host "Módulos do runtime: $modulos"

$destino = Join-Path $raiz 'dist'
if (Test-Path $destino) { Remove-Item $destino -Recurse -Force }
New-Item -ItemType Directory -Path $destino | Out-Null

$comuns = @(
    '--name', 'InstaDino', '--app-version', $Versao, '--vendor', 'InstaDino',
    '--description', 'Análise local da exportação oficial do Instagram',
    '--input', $entrada, '--main-jar', $principal.Name, '--main-class', 'br.com.instadino.desktop.Principal',
    '--java-options', '-Dfile.encoding=UTF-8', '--add-modules', $modulos, '--dest', $destino
)
# 1) imagem de aplicativo: pasta autocontida, sem instalação (base do ZIP e do instalador)
Executar jpackage (@('--type', 'app-image') + $comuns)

$zip = Join-Path $destino "InstaDino-$Versao-windows-x64.zip"
Compress-Archive -Path (Join-Path $destino 'InstaDino') -DestinationPath $zip -CompressionLevel Optimal

$artefatos = @($zip)
if ($Tipo -eq 'exe') {
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
        throw 'O instalador EXE exige o WiX Toolset 3.x (candle.exe) no PATH. Use -Tipo app-image ou instale o WiX.'
    }
    $instalacao = @(
        '--type', 'exe', '--win-menu', '--win-shortcut', '--win-per-user-install', '--win-dir-chooser',
        '--win-upgrade-uuid', 'b7205170-def6-4bba-b5ef-afaf40836b5a'
    )
    Executar jpackage ($instalacao + $comuns)
    $artefatos += (Get-ChildItem $destino -Filter '*.exe' | Where-Object { $_.Name -like 'InstaDino-*' }).FullName
}

if ($Assinar) {
    if (-not $env:INSTADINO_CERT_PFX -or -not $env:INSTADINO_CERT_SENHA) {
        throw 'Para assinar, defina INSTADINO_CERT_PFX (caminho do .pfx) e INSTADINO_CERT_SENHA no ambiente. Nada é lido do repositório.'
    }
    $alvos = @(Join-Path $destino 'InstaDino\InstaDino.exe') + @($artefatos | Where-Object { $_ -like '*.exe' })
    foreach ($alvo in $alvos) {
        Executar signtool @('sign', '/fd', 'SHA256', '/tr', 'http://timestamp.digicert.com', '/td', 'SHA256', '/f', $env:INSTADINO_CERT_PFX, '/p', $env:INSTADINO_CERT_SENHA, $alvo)
        Executar signtool @('verify', '/pa', $alvo)
    }
    # o ZIP precisa ser refeito para conter o EXE assinado
    Remove-Item $zip -Force
    Compress-Archive -Path (Join-Path $destino 'InstaDino') -DestinationPath $zip -CompressionLevel Optimal
} else {
    Write-Warning 'Artefatos NÃO ASSINADOS: o Windows exibirá aviso de editor desconhecido. Não distribua como versão final.'
}

$linhas = foreach ($a in $artefatos) { "{0}  {1}" -f (Get-FileHash $a -Algorithm SHA256).Hash.ToLower(), (Split-Path $a -Leaf) }
$linhas | Set-Content (Join-Path $destino 'SHA256SUMS.txt') -Encoding ascii
Write-Host "Concluído. Artefatos em $destino"
$linhas | ForEach-Object { Write-Host $_ }
