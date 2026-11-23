#!/usr/bin/env bash
# Update AST Code Graph using Graphify and refresh graph.html
set -euo pipefail

echo "🔍 Updating Code Graph via Graphify..."

if ! command -v graphify &> /dev/null; then
    echo "Error: graphify CLI is not found in PATH. Install with: uv tool install graphifyy" >&2
    exit 1
fi

graphify update . --no-cluster "$@"
echo "📊 Exporting interactive HTML visualizer..."
graphify export html

echo "✅ Graph updated successfully in graphify-out/"
