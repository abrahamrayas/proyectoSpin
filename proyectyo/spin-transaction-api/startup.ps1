#Requires -Version 5.1

<#
.SYNOPSIS
Arranca el stack Spin Transaction API: Docker -> Maven -> apps.

.DESCRIPTION
1. Docker: PostgreSQL + Elasticsearch + Logstash + Kibana + Jaeger + Prometheus
2. mvn package -DskipTests (provider-mock, transaction-api)
3. provider-mock :8084 -> transaction-api :8083

.PARAMETER SkipDocker
No levanta contenedores Docker.

.PARAMETER SkipObservability
No espera Elasticsearch/Kibana/Jaeger.

.PARAMETER SkipBuild
Omite mvn package.

.PARAMETER SkipApps
No inicia Spring Boot.

.PARAMETER ApiProfiles
Perfil Spring de transaction-api. Default: observability.
#>

[CmdletBinding()]
param(
    [switch] $SkipDocker,
    [switch] $SkipObservability,
    [switch] $SkipBuild,
    [switch] $SkipApps,
    [string] $ApiProfiles = "observability",
    [int] $HealthTimeoutSec = 180
)

$ErrorActionPreference = "Stop"

$ApiRoot = $PSScriptRoot
$RepoRoot = (Resolve-Path (Join-Path $ApiRoot "..")).Path

$ProviderRoot = if (Test-Path (Join-Path $RepoRoot "provider-mock")) {
    Join-Path $RepoRoot "provider-mock"
}
elseif (Test-Path (Join-Path $RepoRoot "spin-provider-mock")) {
    Join-Path $RepoRoot "spin-provider-mock"
}
else {
    throw "No se encontro provider-mock ni spin-provider-mock en $RepoRoot"
}

$DockerDir = Join-Path $ApiRoot "docker"
$ComposeFile = Join-Path $DockerDir "docker-compose.yml"


function Write-Step {
    param([string] $Message)

    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}


function Test-Command {
    param([string] $Name)

    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}


function Test-TcpPort {
    param([int] $Port)

    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $async.AsyncWaitHandle.WaitOne(500)

        if ($ok -and $client.Connected) {
            $client.Close()
            return $true
        }

        $client.Close()
        return $false
    }
    catch {
        return $false
    }
}


function Test-DockerAvailable {
    if (-not (Test-Command "docker")) {
        return $false
    }

    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"

    try {
        docker info 2>&1 | Out-Null
        return $LASTEXITCODE -eq 0
    }
    finally {
        $ErrorActionPreference = $prev
    }
}


function Wait-ForPort {
    param(
        [string] $Name,
        [int] $Port,
        [int] $TimeoutSec
    )

    Write-Host "Esperando $Name en :$Port (max ${TimeoutSec}s)..." -ForegroundColor DarkGray

    $deadline = (Get-Date).AddSeconds($TimeoutSec)

    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort $Port) {
            Write-Host "  OK $Name :$Port" -ForegroundColor Green
            return
        }

        Start-Sleep -Seconds 2
    }

    throw "Timeout esperando $Name en puerto $Port"
}


function Wait-ForHealth {
    param(
        [string] $Name,
        [string] $Url,
        [int] $TimeoutSec
    )

    Write-Host "Esperando $Name en $Url (max ${TimeoutSec}s)..." -ForegroundColor DarkGray

    $deadline = (Get-Date).AddSeconds($TimeoutSec)

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest `
                -Uri $Url `
                -UseBasicParsing `
                -TimeoutSec 5

            if ($response.StatusCode -eq 200) {
                Write-Host "  OK $Name" -ForegroundColor Green
                return
            }
        }
        catch {
            Start-Sleep -Seconds 3
        }
    }

    throw "Timeout esperando $Name en $Url"
}


function Invoke-ComposeUp {
    if (-not (Test-Path $ComposeFile)) {
        throw "No se encontro: $ComposeFile"
    }

    Write-Step "Docker compose - Postgres + ELK + Jaeger + Prometheus"

    Push-Location $DockerDir

    try {
        $prev = $ErrorActionPreference
        $ErrorActionPreference = "Continue"

        docker compose up -d 2>&1 | Write-Host

        if ($LASTEXITCODE -ne 0) {
            throw "docker compose fallo"
        }
    }
    finally {
        $ErrorActionPreference = $prev
        Pop-Location
    }
}


function Invoke-MavenPackage {
    param(
        [string] $ModulePath,
        [string] $Label
    )

    Write-Step "Maven package - $Label"

    Push-Location $ModulePath

    try {
        mvn -q package -DskipTests

        if ($LASTEXITCODE -ne 0) {
            throw "mvn package fallo en $Label"
        }
    }
    finally {
        Pop-Location
    }
}


function Start-SpringApp {
    param(
        [string] $Title,
        [string] $ModulePath,
        [string] $Profiles
    )

    $mvnRunLine = "mvn spring-boot:run"

    if ($Profiles) {
        $mvnRunLine += " '-Dspring-boot.run.profiles=$Profiles'"
    }

    $command = @"
Set-Location '$ModulePath'
Write-Host '$Title' -ForegroundColor Cyan
$mvnRunLine
"@

    Write-Step "Iniciando $Title"

    Start-Process powershell `
        -ArgumentList @("-NoExit", "-Command", $command) `
        -WindowStyle Normal
}


# ============================================================
# MAIN
# ============================================================

Write-Host @"

Spin Transaction API - startup

API:      $ApiRoot
Provider: $ProviderRoot
Docker:   $DockerDir

"@ -ForegroundColor Yellow


foreach ($path in @($ApiRoot, $ProviderRoot, $DockerDir)) {
    if (-not (Test-Path $path)) {
        throw "Ruta no encontrada: $path"
    }
}


if (-not (Test-Command "mvn")) {
    throw "Maven (mvn) no esta en PATH."
}


# ============================================================
# 1. DOCKER
# ============================================================

if (-not $SkipDocker) {

    if (-not (Test-DockerAvailable)) {

        if (Test-TcpPort 5433) {
            Write-Warning "Docker no disponible pero PostgreSQL responde en :5433. Continuando sin compose."
        }
        else {
            throw @"
Docker Desktop no esta en ejecucion.

1. Abre Docker Desktop
2. Vuelve a ejecutar: .\startup.ps1
3. O usa: .\startup.ps1 -SkipDocker
"@
        }
    }
    else {

        if (-not (Test-TcpPort 5433)) {
            Invoke-ComposeUp
            Wait-ForPort "PostgreSQL" 5433 90
        }
        else {
            Write-Host "PostgreSQL ya responde en :5433" -ForegroundColor DarkGray
        }


        if (-not $SkipObservability) {

            Write-Host "Esperando stack observabilidad (Elasticsearch, Logstash, Jaeger)..." `
                -ForegroundColor DarkGray

            try {
                Wait-ForPort "Elasticsearch" 9200 120
                Wait-ForPort "Kibana" 5601 120
                Wait-ForPort "Logstash" 5000 60
                Wait-ForPort "Jaeger UI" 16686 60
            }
            catch {
                Write-Warning "Observabilidad no lista a tiempo: $($_.Exception.Message). Las apps arrancaran igual."
            }
        }
    }
}


# ============================================================
# 2. BUILD
# ============================================================

if (-not $SkipBuild) {
    Invoke-MavenPackage $ProviderRoot "provider-mock"
    Invoke-MavenPackage $ApiRoot "transaction-api"
}


# ============================================================
# 3. APPS
# ============================================================

if (-not $SkipApps) {

    Start-SpringApp `
        "provider-mock :8084" `
        $ProviderRoot `
        ""

    Wait-ForPort "provider-mock" 8084 90


    Start-SpringApp `
        "transaction-api :8083" `
        $ApiRoot `
        $ApiProfiles

    Wait-ForHealth `
        "transaction-api" `
        "http://localhost:8083/actuator/health" `
        $HealthTimeoutSec
}


Write-Host @"

Listo.

API:        http://localhost:8083/swagger-ui.html
Provider:   http://localhost:8084
Kibana:     http://localhost:5601
Jaeger:     http://localhost:16686
Prometheus: http://localhost:9090

Ejemplo:

curl -X POST http://localhost:8083/transactions `
  -H "Content-Type: application/json" `
  -d '{"accountId":"acc-1","type":"CREDIT","amount":1500,"currency":"MXN","description":"test"}'

"@ -ForegroundColor Green