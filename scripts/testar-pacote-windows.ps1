<#
.SYNOPSIS
  Teste de fumaça do aplicativo empacotado no Windows: inicia, cria o banco e confere a proteção em disco.
.DESCRIPTION
  Usa uma pasta de dados temporária (INSTADINO_DADOS) ou, com -PastaDeDados, o caminho padrão do aplicativo. Verifica que:
    1. o processo continua vivo após alguns segundos (não falhou na inicialização);
    2. o banco foi criado e NÃO começa com "SQLite format 3" (está cifrado);
    3. o arquivo da chave é um blob da DPAPI (assinatura 01 00 00 00 D0 8C 9D DF).
  Não substitui o teste manual de instalação em máquina limpa.
#>
[CmdletBinding()]
param(
    [string]$Executavel = (Join-Path (Split-Path -Parent $PSScriptRoot) 'dist\InstaDino\InstaDino.exe'),
    [int]$Segundos = 25,
    [string]$PastaDeDados,
    [switch]$Manter
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path $Executavel)) { throw "Executável não encontrado: $Executavel" }

# Sem -PastaDeDados usa uma pasta temporária. Com -PastaDeDados e SEM definir INSTADINO_DADOS no processo,
# o aplicativo usa o caminho padrão do Windows (%LOCALAPPDATA%\InstaDinoDados) quando ele for informado aqui.
if ($PastaDeDados) {
    $dados = $PastaDeDados
    Remove-Item Env:INSTADINO_DADOS -ErrorAction SilentlyContinue
} else {
    $dados = Join-Path ([System.IO.Path]::GetTempPath()) ("instadino-fumaca-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $dados | Out-Null
    $env:INSTADINO_DADOS = $dados
}
$processo = $null
try {
    $processo = Start-Process -FilePath $Executavel -PassThru
    Start-Sleep -Seconds $Segundos
    if ($processo.HasExited) { throw "O aplicativo encerrou durante a inicialização (código $($processo.ExitCode))." }
    Write-Host "OK: processo vivo após $Segundos s."

    $banco = Join-Path $dados 'instadino.db'
    $chave = Join-Path $dados 'chave-do-banco.protegida'
    if (-not (Test-Path $banco)) { throw 'O banco local não foi criado.' }
    if (-not (Test-Path $chave)) { throw 'O arquivo da chave não foi criado.' }

    $cabecalho = [System.IO.File]::ReadAllBytes($banco)[0..14]
    if ([System.Text.Encoding]::ASCII.GetString($cabecalho) -eq 'SQLite format 3') { throw 'FALHA GRAVE: o banco está em claro.' }
    Write-Host 'OK: banco sem o cabeçalho SQLite em claro (cifrado).'

    $esperado = [byte[]](0x01, 0x00, 0x00, 0x00, 0xD0, 0x8C, 0x9D, 0xDF)
    $inicio = [System.IO.File]::ReadAllBytes($chave)[0..7]
    if (Compare-Object $esperado $inicio -SyncWindow 0) { throw 'O arquivo da chave não é um blob da DPAPI.' }
    Write-Host 'OK: chave protegida pela DPAPI do Windows.'
} finally {
    if ($processo -and -not $processo.HasExited) { Stop-Process -Id $processo.Id -Force }
    Start-Sleep -Seconds 1
    if (-not $Manter) { Remove-Item $dados -Recurse -Force -ErrorAction SilentlyContinue }
}
Write-Host 'Teste de fumaça concluído com sucesso.'
