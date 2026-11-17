<#
.SYNOPSIS
    Stops the Local Java Microservices Platform (Docker Compose or Kubernetes).
#>

param(
    [Parameter(Position=0)]
    [Alias("m")]
    [ValidateSet("docker", "k8s", "kubernetes", "Kubernetes", "Docker", "K8s", IgnoreCase=$true)]
    [string]$Mode = "k8s"
)

$ProjectRoot = (Get-Item "$PSScriptRoot\..").FullName
Set-Location $ProjectRoot

$ModeLower = $Mode.ToLower()
$IsK8s = ($ModeLower -in @("k8s", "kubernetes"))

Write-Host "Stopping Microservices Platform ($(if ($IsK8s) { 'KUBERNETES' } else { 'DOCKER' }) mode)..." -ForegroundColor Yellow

if (-not $IsK8s) {
    docker compose -f "$ProjectRoot\docker\docker-compose.yml" down -v
} else {
    # Stop any background port-forward processes
    Get-CimInstance Win32_Process -Filter "Name = 'kubectl.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*port-forward*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    kubectl delete -k "$ProjectRoot\k8s" --ignore-not-found=true
}

Write-Host "[✓] Platform stopped." -ForegroundColor Green
