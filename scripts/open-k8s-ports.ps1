<#
.SYNOPSIS
    Opens and maintains all local development ports from Kubernetes to localhost.
    Allows host access to Redis, Kafka, PostgreSQL, LocalStack, Keycloak, and Microservices.
#>

param(
    [string]$Namespace = "microservices-demo",
    [switch]$KeepAlive
)

$ErrorActionPreference = "Continue"

Write-Host "Opening local development ports for Kubernetes in namespace '$Namespace'..." -ForegroundColor Cyan

# Define service port mappings (LocalPort:ServicePort)
$portMappings = @(
    @{ Service = "control-center";       LocalPort = 8090; SvcPort = 8090; Name = "Control Center" },
    @{ Service = "api-gateway";          LocalPort = 8080; SvcPort = 8080; Name = "API Gateway" },
    @{ Service = "keycloak";             LocalPort = 8088; SvcPort = 8080; Name = "Keycloak IAM" },
    @{ Service = "aws-emulator";         LocalPort = 4566; SvcPort = 4566; Name = "AWS Emulator (LocalStack)" },
    @{ Service = "inventory-redis";      LocalPort = 6379; SvcPort = 6379; Name = "Redis Cache & Locks" },
    @{ Service = "kafka";                LocalPort = 9092; SvcPort = 29092; Name = "Apache Kafka" },
    @{ Service = "order-db";             LocalPort = 5432; SvcPort = 5432; Name = "Order DB (Postgres)" },
    @{ Service = "product-db";           LocalPort = 5433; SvcPort = 5432; Name = "Product DB (Postgres)" },
    @{ Service = "payment-db";           LocalPort = 5434; SvcPort = 5432; Name = "Payment DB (Postgres)" },
    @{ Service = "order-service";        LocalPort = 8081; SvcPort = 8081; Name = "Order Service" },
    @{ Service = "inventory-service";    LocalPort = 8082; SvcPort = 8082; Name = "Inventory Service" },
    @{ Service = "notification-service"; LocalPort = 8083; SvcPort = 8083; Name = "Notification Service" },
    @{ Service = "payment-service";      LocalPort = 8084; SvcPort = 8084; Name = "Payment Service" },
    @{ Service = "product-service";      LocalPort = 8085; SvcPort = 8085; Name = "Product Service" }
)

# Terminate existing kubectl port-forward processes
Get-CimInstance Win32_Process -Filter "Name = 'kubectl.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*port-forward*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

Start-Sleep -Milliseconds 500

foreach ($pm in $portMappings) {
    Write-Host "  -> Forwarding localhost:$($pm.LocalPort) -> svc/$($pm.Service):$($pm.SvcPort) ($($pm.Name))..." -ForegroundColor Gray
    Start-Process -FilePath "kubectl" -ArgumentList "port-forward", "svc/$($pm.Service)", "$($pm.LocalPort):$($pm.SvcPort)", "-n", $Namespace -WindowStyle Hidden
}

Start-Sleep -Milliseconds 800

Write-Host "`nVerifying port connectivity on localhost (parallel async check)..." -ForegroundColor Cyan

function Test-PortsParallel {
    param([array]$mappings, [int]$timeoutMs = 600)
    $probes = @()
    foreach ($pm in $mappings) {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect("127.0.0.1", $pm.LocalPort, $null, $null)
        $probes += [PSCustomObject]@{
            Client    = $client
            IAR       = $iar
            LocalPort = $pm.LocalPort
            Name      = $pm.Name
            Connected = $false
        }
    }

    foreach ($p in $probes) {
        $ok = $p.IAR.AsyncWaitHandle.WaitOne($timeoutMs, $false)
        if ($ok -and $p.Client.Connected) {
            try { $p.Client.EndConnect($p.IAR) } catch {}
            $p.Connected = $true
        }
        $p.Client.Close()
    }
    return $probes
}

$results = Test-PortsParallel $portMappings 600

# Quick retry for any still establishing
$unconnected = $results | Where-Object { -not $_.Connected }
if ($unconnected.Count -gt 0) {
    Start-Sleep -Milliseconds 500
    $retryMappings = $portMappings | Where-Object { $_.LocalPort -in $unconnected.LocalPort }
    $retryResults = Test-PortsParallel $retryMappings 600
    foreach ($rr in $retryResults) {
        if ($rr.Connected) {
            $match = $results | Where-Object { $_.LocalPort -eq $rr.LocalPort }
            if ($match) { $match.Connected = $true }
        }
    }
}

foreach ($r in $results) {
    if ($r.Connected) {
        Write-Host "  [✓] localhost:$($r.LocalPort) ($($r.Name)) is OPEN" -ForegroundColor Green
    } else {
        Write-Host "  [!] localhost:$($r.LocalPort) ($($r.Name)) still initializing" -ForegroundColor Yellow
    }
}

Write-Host "`nAll local development ports configured successfully!" -ForegroundColor Green

if ($KeepAlive) {
    Write-Host "Keeping port-forward connections alive in background with self-healing..." -ForegroundColor Cyan
    while ($true) {
        Start-Sleep -Seconds 10
        $health = Test-PortsParallel $portMappings 400
        $dropped = $health | Where-Object { -not $_.Connected }
        foreach ($d in $dropped) {
            $pm = $portMappings | Where-Object { $_.LocalPort -eq $d.LocalPort } | Select-Object -First 1
            if ($pm) {
                # Revive dropped port-forward
                Start-Process -FilePath "kubectl" -ArgumentList "port-forward", "svc/$($pm.Service)", "$($pm.LocalPort):$($pm.SvcPort)", "-n", $Namespace -WindowStyle Hidden
            }
        }
    }
}
