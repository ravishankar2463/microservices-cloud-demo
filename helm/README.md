# Cloud-Native Microservices Helm Packaging

This directory contains production-ready Kubernetes packaging for the decoupled Java 25 microservice ecosystem.

---

## 📦 Chart Structure

```text
helm/
└── ecommerce-platform/          # Umbrella Helm chart orchestrating all microservices
    ├── Chart.yaml               # Metadata, version 1.0.0
    ├── values.yaml              # Default baseline configuration
    ├── values-dev.yaml          # Developer environment sizing (single replica, debug traces)
    ├── values-prod.yaml         # Production sizing (HPA autoscaling, replicas: 3, resource bounds)
    └── templates/
        ├── _helpers.tpl         # Standardized Kubernetes labeling and naming templates
        ├── configmap.yaml       # Shared cloud endpoints & telemetry variables
        ├── services.yaml        # Service definitions for all 7 microservices
        ├── deployments.yaml     # Autonomous microservice deployments with liveness/readiness probes
        └── ingress.yaml         # Edge Ingress routing traffic to API Gateway and Control Center
```

---

## 🚀 Deployment Instructions

### 1. Lint the Helm Chart
```bash
helm lint helm/ecommerce-platform
```

### 2. Deploy Local / Development Stack
```bash
helm upgrade --install ecommerce-demo ./helm/ecommerce-platform -f ./helm/ecommerce-platform/values-dev.yaml
```

### 3. Deploy Production-Ready Stack (Multi-Replica + HPA)
```bash
helm upgrade --install ecommerce-prod ./helm/ecommerce-platform -f ./helm/ecommerce-platform/values-prod.yaml --namespace production --create-namespace
```

### 4. Verify Pod Health
```bash
kubectl get pods -l app.kubernetes.io/instance=ecommerce-demo
```
