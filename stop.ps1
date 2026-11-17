<#
.SYNOPSIS
    Stops the Local Java Microservices Platform (Docker Compose or Kubernetes).
#>

param(
    [Parameter(Position=0)]
    [Alias("m")]
    [ValidateSet("docker", "k8s", "kubernetes", "Kubernetes", "Docker", "K8s", IgnoreCase=$true)]
    [string]$Mode = "docker"
)

& "$PSScriptRoot\scripts\stop-local.ps1" -Mode $Mode
