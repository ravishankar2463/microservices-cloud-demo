#!/usr/bin/env bash
RAW_MODE="${1:-k8s}"
MODE_LOWER="$(echo "$RAW_MODE" | tr '[:upper:]' '[:lower:]')"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$PROJECT_ROOT"

if [ "$MODE_LOWER" = "k8s" ] || [ "$MODE_LOWER" = "kubernetes" ]; then
    echo "Stopping Microservices Platform (KUBERNETES mode)..."
    pkill -f "kubectl port-forward" 2>/dev/null || true
    kubectl delete -k k8s --ignore-not-found=true
else
    echo "Stopping Microservices Platform (DOCKER mode)..."
    docker compose -f docker/docker-compose.yml down -v
fi

echo "[✓] Platform stopped."
