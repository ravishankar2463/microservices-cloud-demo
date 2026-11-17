<#
.SYNOPSIS
    Starts the Local Java Microservices Platform via Docker Compose or Kubernetes and opens the Control Center Dashboard.

.PARAMETER Mode
    Deployment mode: "k8s" (default) or "docker".
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
    [string]$Mode = "k8s",

    [switch]$SkipBuild,
    [switch]$NoBrowser
)

& "$PSScriptRoot\scripts\start-local.ps1" -Mode $Mode -SkipBuild:$SkipBuild -NoBrowser:$NoBrowser
