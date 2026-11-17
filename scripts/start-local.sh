#!/usr/bin/env bash
set -e

RAW_MODE="${1:-docker}"
MODE_LOWER="$(echo "$RAW_MODE" | tr '[:upper:]' '[:lower:]')"
SKIP_BUILD="${2:-false}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$PROJECT_ROOT"

if [ "$MODE_LOWER" = "k8s" ] || [ "$MODE_LOWER" = "kubernetes" ]; then
    DEPLOY_MODE="KUBERNETES"
    IS_K8S=true
else
    DEPLOY_MODE="DOCKER COMPOSE"
    IS_K8S=false
fi

echo "========================================================================"
echo " ⚡ Local Microservices Control Center & Platform Launcher"
echo " Mode: $DEPLOY_MODE | Root: $PROJECT_ROOT"
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
if [ "$IS_K8S" = "false" ]; then
    echo "[+] Starting containers with Docker Compose..."
    docker compose -f docker/docker-compose.yml up -d --build
else
    echo "[+] Checking Kubernetes cluster..."
    kubectl cluster-info
    
    echo "[+] Building local container images for Kubernetes..."
    SERVICES=(
        "services/api-gateway:api-gateway:api-gateway-0.0.1-SNAPSHOT.jar"
        "services/order-service:order-service:order-service-0.0.1-SNAPSHOT.jar"
        "services/inventory-service:inventory-service:inventory-service-0.0.1-SNAPSHOT.jar"
        "services/notification-service:notification-service:notification-service-0.0.1-SNAPSHOT.jar"
        "services/payment-service:payment-service:payment-service-0.0.1-SNAPSHOT.jar"
        "services/product-service:product-service:product-service-0.0.1-SNAPSHOT.jar"
        "services/control-center:control-center:control-center-0.0.1-SNAPSHOT.jar"
    )

    for item in "${SERVICES[@]}"; do
        IFS=":" read -r mod name jar <<< "$item"
        echo "  -> Building image $name:latest..."
        docker build -t "$name:latest" -f docker/Dockerfile --build-arg SERVICE_MODULE="$mod" --build-arg APP_JAR="$jar" . >/dev/null
    done

    echo "[+] Applying Kubernetes manifests via Kustomize..."
    kubectl apply -k k8s
    
    echo "[+] Waiting for control-center deployment..."
    kubectl rollout status deployment/control-center -n microservices-demo --timeout=90s
    
    echo "[+] Starting background port-forward for control-center..."
    kubectl port-forward svc/control-center 8090:8090 -n microservices-demo >/dev/null 2>&1 &
    DASHBOARD_URL="http://localhost:8090"
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
