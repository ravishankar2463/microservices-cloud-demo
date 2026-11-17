<#
.SYNOPSIS
    Stops the Local Java Microservices Platform (Docker Compose or Kubernetes).
#>

param(
    [ValidateSet("docker", "k8s", "kubernetes")]
    [string]$Mode = "docker"
)

$ProjectRoot = (Get-Item "$PSScriptRoot\..").FullName
Set-Location $ProjectRoot

Write-Host "Stopping Microservices Platform ($($Mode.ToUpper()) mode)..." -ForegroundColor Yellow

if ($Mode -eq "docker") {
    docker compose -f "$ProjectRoot\docker\docker-compose.yml" down -v
} else {
    kubectl delete -k "$ProjectRoot\k8s" --ignore-not-found=true
}

Write-Host "[✓] Platform stopped." -ForegroundColor Green
