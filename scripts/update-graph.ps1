<#
.SYNOPSIS
    Re-extracts the AST code graph using Graphify and regenerates the interactive HTML visualization.

.DESCRIPTION
    Runs graphify in non-clustering mode (fast AST extraction without requiring LLM keys)
    and updates graphify-out/graph.json and graphify-out/graph.html.

.EXAMPLE
    .\scripts\update-graph.ps1
#>

[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

Write-Host "🔍 Updating Code Graph via Graphify..." -ForegroundColor Cyan

if (-not (Get-Command graphify -ErrorAction SilentlyContinue)) {
    Write-Error "graphify CLI is not found in PATH. Install with: uv tool install graphifyy"
    exit 1
}

$updateArgs = @("update", ".", "--no-cluster")
if ($Force) {
    $updateArgs += "--force"
}

& graphify @updateArgs

if ($LASTEXITCODE -ne 0) {
    Write-Warning "Graphify update exited with code $LASTEXITCODE"
}

Write-Host "📊 Exporting interactive HTML visualizer..." -ForegroundColor Cyan
& graphify export html

if (Test-Path "graphify-out/graph.html") {
    Write-Host "`n✅ Graph updated successfully!" -ForegroundColor Green
    Write-Host "   • Graph Data:  graphify-out/graph.json" -ForegroundColor Gray
    Write-Host "   • Interactive: graphify-out/graph.html" -ForegroundColor Gray
    Write-Host "   • CLI Query:   graphify query '<question>'" -ForegroundColor Gray
}
