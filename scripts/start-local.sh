#!/usr/bin/env bash
set -e

MODE="${1:-docker}"
SKIP_BUILD="${2:-false}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$PROJECT_ROOT"

echo "========================================================================"
echo " ⚡ Local Microservices Control Center & Platform Launcher"
echo " Mode: ${MODE^^} | Root: $PROJECT_ROOT"
echo "========================================================================"

# 1. Check Docker
if ! docker info >/dev/null 2>&1; then
    echo "[-] Error: Docker daemon is not running. Please start Docker."
    exit 1
fi
echo "[+] Docker daemon is running."

# 2. Build JARs
if [ "$SKIP_BUILD" != "true" ]; then
    echo "[+] Packaging microservices with Maven wrapper..."
    ./mvnw clean package -DskipTests
    echo "[+] Maven packaging complete."
fi

# 3. Deploy
DASHBOARD_URL="http://localhost:8090"
if [ "$MODE" = "docker" ]; then
    echo "[+] Starting containers with Docker Compose..."
    docker compose -f docker/docker-compose.yml up -d --build
else
    echo "[+] Applying Kubernetes manifests via Kustomize..."
    kubectl apply -k k8s
    echo "[+] Waiting for control-center deployment..."
    kubectl rollout status deployment/control-center -n microservices-demo --timeout=90s
    DASHBOARD_URL="http://localhost:30090"
fi

# 4. Wait for health
echo "[+] Waiting for Control Center at $DASHBOARD_URL..."
for i in {1..30}; do
    if curl -s -f "$DASHBOARD_URL/actuator/health" >/dev/null 2>&1; then
        echo "[+] Control Center is HEALTHY!"
        break
    fi
    echo -n "."
    sleep 2
done
echo ""

echo "========================================================================"
echo " 🌟 PLATFORM RUNNING - Control Center Dashboard: $DASHBOARD_URL"
echo "========================================================================"

# 5. Open browser if desktop environment available
if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$DASHBOARD_URL"
elif command -v open >/dev/null 2>&1; then
    open "$DASHBOARD_URL"
fi
