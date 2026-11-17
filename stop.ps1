param(
    [ValidateSet("docker", "k8s", "kubernetes")]
    [string]$Mode = "docker"
)

& "$PSScriptRoot\scripts\stop-local.ps1" -Mode $Mode
