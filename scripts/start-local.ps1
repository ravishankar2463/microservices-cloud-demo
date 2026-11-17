<#
.SYNOPSIS
    Starts the Local Java Microservices Platform via Docker Compose or Kubernetes and opens the Control Center Dashboard.

.PARAMETER Mode
    Deployment mode: "docker" (default) or "k8s" / "kubernetes" / "Kubernetes".
    Supports positional argument:
      .\start.ps1
      .\start.ps1 docker
      .\start.ps1 k8s
      .\start.ps1 kubernetes
      .\start.ps1 -Mode k8s
      .\start.ps1 -Mode Kubernetes

.PARAMETER SkipBuild
    Skips the Maven packaging phase (uses existing target/*.jar artifacts).

.PARAMETER NoBrowser
    Prevents automatically opening the dashboard in the default browser.
#>

param(
    [Parameter(Position=0)]
    [Alias("m")]
    [ValidateSet("docker", "k8s", "kubernetes", "Kubernetes", "Docker", "K8s", IgnoreCase=$true)]
    [string]$Mode = "docker",

    [switch]$SkipBuild,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Get-Item "$PSScriptRoot\..").FullName
Set-Location $ProjectRoot

# Normalize mode
$ModeLower = $Mode.ToLower()
$IsK8s = ($ModeLower -in @("k8s", "kubernetes"))
$TargetModeDisplay = if ($IsK8s) { "KUBERNETES" } else { "DOCKER COMPOSE" }

function Write-Step {
    param([string]$Message)
    Write-Host "`n[+] $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "[✓] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[!] $Message" -ForegroundColor Yellow
}

function Write-Err {
    param([string]$Message)
    Write-Host "[✗] $Message" -ForegroundColor Red
}

Write-Host @"
========================================================================
 ⚡ Local Microservices Control Center & Platform Launcher
 Mode: $TargetModeDisplay | Root: $ProjectRoot
========================================================================
"@ -ForegroundColor DarkCyan

# -----------------------------------------------------------------------------
# 1. Check Prerequisites
# -----------------------------------------------------------------------------
Write-Step "Checking local environment prerequisites..."

# Check Docker daemon & WSL health with auto-recovery
try {
    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Docker daemon not responding."
    }
    Write-Success "Docker daemon is active and responsive."
} catch {
    Write-Warn "Docker daemon is not responsive. Attempting automatic WSL2 recovery..."
    try {
        & wsl.exe --shutdown | Out-Null
        Start-Sleep -Seconds 2
        & wsl.exe -d docker-desktop -e echo "WSL wake" | Out-Null
        if (Test-Path "C:\Program Files\Docker\Docker\Docker Desktop.exe") {
            Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
            Write-Host "Waiting 15 seconds for Docker Desktop to boot..." -ForegroundColor Gray
            Start-Sleep -Seconds 15
        }
        $dockerInfo = docker info 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Docker recovery failed."
        }
        Write-Success "Docker daemon successfully recovered and active."
    } catch {
        Write-Err "Docker daemon is not running! Please start Docker Desktop and retry."
        exit 1
    }
}

# Check Java runtime
try {
    $javaVersion = java -version 2>&1 | Out-String
    Write-Success "Java runtime detected."
} catch {
    Write-Warn "Java not found on PATH; relying on local Maven wrapper / containers."
}

# -----------------------------------------------------------------------------
# 2. Build Microservice JARs
# -----------------------------------------------------------------------------
if (-not $SkipBuild) {
    Write-Step "Packaging microservice modules with Maven wrapper (skipping tests for rapid startup)..."
    & "$ProjectRoot\mvnw.cmd" clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Maven packaging failed. Please resolve build errors before starting."
        exit 1
    }
    Write-Success "All microservice JARs packaged successfully."
} else {
    Write-Warn "Skipping Maven build (-SkipBuild specified). Using existing JAR artifacts."
}

# -----------------------------------------------------------------------------
# 3. Deploy Platform (Docker or Kubernetes)
# -----------------------------------------------------------------------------
$DashboardUrl = "http://localhost:8090"

if (-not $IsK8s) {
    Write-Step "Starting infrastructure and microservices with Docker Compose..."
    docker compose -f "$ProjectRoot\docker\docker-compose.yml" up -d --build
    if ($LASTEXITCODE -ne 0) {
        Write-Err "docker compose up failed."
        exit 1
    }
    Write-Success "Docker containers launched in background."

} else {
    Write-Step "Checking Kubernetes cluster connectivity..."
    try {
        $clusterInfo = kubectl cluster-info 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Kubernetes cluster unreachable."
        }
        $nodes = kubectl get nodes --no-headers 2>&1 | Out-String
        Write-Success "Kubernetes cluster connected! Active nodes:`n$nodes"
    } catch {
        Write-Err "Unable to connect to a local Kubernetes cluster!
Please ensure Kubernetes is enabled in Docker Desktop (Settings -> Kubernetes -> Enable),
or start a local cluster via 'minikube start' or 'kind create cluster'.
Tip: You can start immediately using Docker Compose by running:
    .\start.ps1 -Mode docker"
        exit 1
    }

    Write-Step "Building local container images for Kubernetes..."
    $servicesToBuild = @(
        @{ Name = "api-gateway"; Module = "services/api-gateway"; Jar = "api-gateway-0.0.1-SNAPSHOT.jar" },
        @{ Name = "order-service"; Module = "services/order-service"; Jar = "order-service-0.0.1-SNAPSHOT.jar" },
        @{ Name = "inventory-service"; Module = "services/inventory-service"; Jar = "inventory-service-0.0.1-SNAPSHOT.jar" },
        @{ Name = "notification-service"; Module = "services/notification-service"; Jar = "notification-service-0.0.1-SNAPSHOT.jar" },
        @{ Name = "payment-service"; Module = "services/payment-service"; Jar = "payment-service-0.0.1-SNAPSHOT.jar" },
        @{ Name = "product-service"; Module = "services/product-service"; Jar = "product-service-0.0.1-SNAPSHOT.jar" },
        @{ Name = "control-center"; Module = "services/control-center"; Jar = "control-center-0.0.1-SNAPSHOT.jar" }
    )

    $isKindCluster = (docker ps -q -f name=desktop-control-plane)
    foreach ($svc in $servicesToBuild) {
        Write-Host "  -> Building image $($svc.Name):latest..." -ForegroundColor Gray
        docker build -t "$($svc.Name):latest" -f "$ProjectRoot\docker\Dockerfile" `
            --build-arg SERVICE_MODULE="$($svc.Module)" `
            --build-arg APP_JAR="$($svc.Jar)" "$ProjectRoot" | Out-Null

        if ($isKindCluster) {
            Write-Host "     Loading image into local Kubernetes node (desktop-control-plane)..." -ForegroundColor DarkGray
            $tarFile = "$ProjectRoot\$($svc.Name).tar"
            docker save -o $tarFile "$($svc.Name):latest"
            docker cp $tarFile "desktop-control-plane:/tmp/$($svc.Name).tar"
            docker exec desktop-control-plane ctr -n k8s.io images import "/tmp/$($svc.Name).tar" | Out-Null
            docker exec desktop-control-plane rm "/tmp/$($svc.Name).tar" | Out-Null
            Remove-Item $tarFile -Force -ErrorAction SilentlyContinue
        }
    }
    Write-Success "All 7 local microservice container images built."

    Write-Step "Applying Kubernetes manifests via Kustomize..."
    kubectl apply -k "$ProjectRoot\k8s"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "kubectl apply failed."
        exit 1
    }

    Write-Step "Waiting for control-center rollout in namespace 'microservices-demo'..."
    kubectl rollout status deployment/control-center -n microservices-demo --timeout=90s

    Write-Step "Configuring port-forwarding for Control Center Dashboard..."
    # Ensure any previous port-forward process is stopped
    Get-Process -Name kubectl -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*port-forward*8090*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    
    # Start port-forward in background job
    Start-Job -ScriptBlock {
        kubectl port-forward svc/control-center 8090:8090 -n microservices-demo
    } | Out-Null
    
    $DashboardUrl = "http://localhost:8090"
    Write-Success "Kubernetes services deployed. NodePort: 30090, Direct Port: 8090."
}

# -----------------------------------------------------------------------------
# 4. Wait for Control Center Dashboard Health
# -----------------------------------------------------------------------------
Write-Step "Waiting for Control Center & Dashboard to become ready at $DashboardUrl..."
$maxAttempts = 30
$attempt = 0
$isHealthy = $false

while ($attempt -lt $maxAttempts -and -not $isHealthy) {
    $attempt++
    try {
        $response = Invoke-WebRequest -Uri "$DashboardUrl/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            $isHealthy = $true
            break
        }
    } catch {
        # Retry
    }
    Write-Host -NoNewline "."
    Start-Sleep -Seconds 2
}
Write-Host ""

if ($isHealthy) {
    Write-Success "Control Center is HEALTHY and ready!"
} else {
    Write-Warn "Control Center health probe did not return 200 within timeout. Services may still be initializing."
}

# -----------------------------------------------------------------------------
# 5. Service Directory Summary & Dashboard Launch
# -----------------------------------------------------------------------------
Write-Host @"

========================================================================
 🌟 PLATFORM RUNNING - SERVICE ENDPOINTS & ACCESS ($TargetModeDisplay)
========================================================================
 Control Center Dashboard: $DashboardUrl (and http://localhost:30090 in K8s)
 API Gateway:              http://localhost:8080 (NodePort: 30080 in K8s)
 Order Service:            http://localhost:8081
 Inventory Service:        http://localhost:8082
 Notification Service:     http://localhost:8083
 Payment Service:          http://localhost:8084
 Product Service:          http://localhost:8085
 Keycloak IAM:             http://localhost:8088 (NodePort: 30088 in K8s)
 LocalStack AWS Emulator:  http://localhost:4566
 Apache Kafka:             localhost:9092
 Redis Cache:              localhost:6379
 PostgreSQL Databases:     Order (5432), Product (5433), Payment (5434)
========================================================================
"@ -ForegroundColor Green

if (-not $NoBrowser) {
    Write-Step "Opening Control Center Dashboard in your default browser..."
    Start-Process $DashboardUrl
}

Write-Success "Platform startup complete. Enjoy exploring the architecture!"
