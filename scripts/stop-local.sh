#!/usr/bin/env bash
MODE="${1:-docker}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$PROJECT_ROOT"
echo "Stopping Microservices Platform ($MODE mode)..."

if [ "$MODE" = "docker" ]; then
    docker compose -f docker/docker-compose.yml down -v
else
    kubectl delete -k k8s --ignore-not-found=true
fi

echo "[✓] Platform stopped."
