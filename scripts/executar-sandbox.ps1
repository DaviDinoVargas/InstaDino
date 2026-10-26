# Sobe o backend de SANDBOX local e o aplicativo desktop no Windows (PowerShell).
# Uso: .\scripts\executar-sandbox.ps1 [-SomenteBackend]
# Requisitos: JDK 21 e Maven 3.9+ no PATH.
#
# Segurança: o backend escuta só em 127.0.0.1; nenhuma senha é escrita no log. As credenciais das contas de
# demonstração ficam em dados-backend\contas-semeadas.txt (ignorado pelo Git).
# Para usar senhas suas, defina antes $env:INSTADINO_SENHA_MASTER, $env:INSTADINO_SENHA_PREMIUM e $env:INSTADINO_SENHA_FREE.
param([switch]$SomenteBackend)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

if (-not $env:INSTADINO_DADOS_BACKEND) { $env:INSTADINO_DADOS_BACKEND = Join-Path $raiz 'dados-backend' }
$porta = if ($env:INSTADINO_PORTA) { $env:INSTADINO_PORTA } else { '8443' }

Write-Host '==> Compilando (sem testes)...'
mvn -B -q -DskipTests install -pl backend-api,desktop-app -am
if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação.' }

Write-Host "==> Iniciando o backend de sandbox em http://127.0.0.1:$porta ..."
$env:SPRING_PROFILES_ACTIVE = 'desenvolvimento'
$env:INSTADINO_PORTA = $porta
$env:PAYMENTS_ENABLED = 'true'
$env:PAYMENTS_MODE = 'sandbox'
New-Item -ItemType Directory -Force -Path $env:INSTADINO_DADOS_BACKEND | Out-Null
$log = Join-Path $env:INSTADINO_DADOS_BACKEND 'backend.log'
$jar = Join-Path $raiz 'backend-api\target\backend-api-0.1.0-SNAPSHOT.jar'
$backend = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar) -PassThru -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError "$log.err"
try {
    $pronto = $false
    for ($i = 0; $i -lt 60; $i++) {
        try { Invoke-RestMethod "http://127.0.0.1:$porta/v1/status" | Out-Null; $pronto = $true; break } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $pronto) { throw "O backend não iniciou. Veja $log" }
    Write-Host "==> Backend pronto. Contas de demonstração: $env:INSTADINO_DADOS_BACKEND\contas-semeadas.txt"
    if ($SomenteBackend) { Read-Host 'Pressione Enter para encerrar o backend'; return }

    Write-Host '==> Iniciando o desktop...'
    $env:INSTADINO_API = "http://127.0.0.1:$porta"
    $env:INSTADINO_CHAVES_CONFIAVEIS = Join-Path $env:INSTADINO_DADOS_BACKEND 'chaves-dev'
    $env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Dinstadino.modoInseguroDesenvolvimento=true"
    mvn -B -q -pl desktop-app javafx:run
}
finally {
    if ($backend -and -not $backend.HasExited) { Stop-Process -Id $backend.Id -Force }
}
