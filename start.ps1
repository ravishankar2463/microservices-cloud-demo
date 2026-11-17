param(
    [ValidateSet("docker", "k8s", "kubernetes")]
    [string]$Mode = "docker",
    [switch]$SkipBuild,
    [switch]$NoBrowser
)

& "$PSScriptRoot\scripts\start-local.ps1" -Mode $Mode -SkipBuild:$SkipBuild -NoBrowser:$NoBrowser
