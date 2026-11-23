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
    Skips Maven and Docker builds completely (uses existing JARs and container images).

.PARAMETER ForceBuild
    Bypasses per-folder Git diff change detection and forces rebuilding all 7 microservices.

.PARAMETER NoBrowser
    Prevents automatically opening the dashboard in the default browser.

.PARAMETER EnableAuth
    Enables Keycloak OAuth2 JWT resource server authentication on the API Gateway (default is disabled for friction-free local development).
#>

param(
    [Parameter(Position=0)]
    [Alias("m")]
    [ValidateSet("docker", "k8s", "kubernetes", "Kubernetes", "Docker", "K8s", IgnoreCase=$true)]
    [string]$Mode = "k8s",

    [switch]$SkipBuild,
    [switch]$ForceBuild,
    [switch]$NoBrowser,
    [switch]$EnableAuth
)

& "$PSScriptRoot\scripts\start-local.ps1" -Mode $Mode -SkipBuild:$SkipBuild -ForceBuild:$ForceBuild -NoBrowser:$NoBrowser -EnableAuth:$EnableAuth
