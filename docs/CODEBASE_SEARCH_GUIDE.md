# 🧭 Codebase Navigation & Tooling Guide

This repository contains multiple decoupled microservices, shared libraries, container recipes, and Kubernetes configurations. To explore, search, and navigate the system efficiently without cognitive overload or heavy IDE indexing delays, modern CLI utilities are configured and ready.

---

## 📋 Overview of Installed Navigation Tools

| Tool | Focus Area | Best For | Typical Execution Time |
| :--- | :--- | :--- | :--- |
| **Graphify** | Dependency Graph & Architecture | Tracing call paths, finding callers/callees, exploring microservice relationships | < 1 second |
| **Ripgrep (`rg`)** | Full-Text Regex Search | Finding string literals, annotations, config keys, error codes | < 100 ms |
| **`fd`** | File and Directory Finding | Locating files by name, extension, or directory pattern | < 50 ms |
| **`ast-grep` (`sg`)** | Structural AST Search | Matching Java code patterns, method structures, specific API calls | < 500 ms |
| **`jq`** | JSON Stream Processing | Extracting fields from manifests, Docker inspections, and k8s JSON | < 20 ms |

---

## 1. 🕸️ Graphify: Code Knowledge Graph

Graphify parses the codebase's Abstract Syntax Tree (AST) to construct a queryable knowledge graph stored in `graphify-out/graph.json`. It requires **no local LLM or server running**.

### Exploring the Interactive Graph UI
Open the generated visualizer in your default browser:
```powershell
# Open in default web browser (Windows)
Start-Process "graphify-out\graph.html"
```
The browser view allows interactive zooming, node filtering, and visual exploration of service components, models, repositories, and controllers.

### CLI Query Commands
- **Semantic / Architecture Search**:
  ```bash
  graphify query "order saga orchestrator"
  graphify query "inventory" --budget 3000
  ```
- **Trace Relationship Between Components**:
  ```bash
  graphify path "services_order_service_src_main_java_com_ravi_order_saga_orchestration_ordersagaorchestrator_ordersagaorchestrator" "services_order_service_src_main_java_com_ravi_order_application_command_handler_ordercommandhandler_ordercommandhandler"
  ```
- **Inspect Connections of a Node**:
  ```bash
  graphify explain "OrderCommandHandler"
  ```
- **Updating the Graph After Code Edits**:
  ```powershell
  # Fast rebuild (PowerShell)
  .\scripts\update-graph.ps1

  # Or on Linux / macOS
  ./scripts/update-graph.sh
  ```

---

## 2. ⚡ Ripgrep (`rg`): High-Speed Code Search

Ripgrep is pre-installed on this machine (`rg.exe 15.2.0`). It automatically respects `.gitignore` and skips binary or cached files.

### Key Search Patterns for this Repo

- **Find Spring Services and Components**:
  ```bash
  rg "@(Service|Component|Repository|RestController)" services/
  ```

- **Find Outbox & Saga Events**:
  ```bash
  rg "OutboxJpaEntity" services/
  rg "OrderSaga" services/
  ```

- **Find Sealed Interfaces and Records**:
  ```bash
  rg "sealed interface" common/ services/
  rg "public record" common/ services/
  ```

- **Find Event Listeners (Kafka / AWS SQS / SNS)**:
  ```bash
  rg "@KafkaListener" services/
  rg "@SqsListener" services/
  ```

- **Filter by File Extension**:
  ```bash
  # Only Java files
  rg "saveToOutbox" -g "*.java"

  # Only configuration YAMLs
  rg "localstack" -g "*.{yaml,yml}"
  ```

---

## 3. 📂 `fd`: Instant File Finding

`fd` is a simple, fast replacement for find.

### Common Examples
- **Find all Dockerfiles**:
  ```bash
  fd "Dockerfile"
  ```
- **Find all POM files across microservices**:
  ```bash
  fd "pom.xml"
  ```
- **Find all integration tests**:
  ```bash
  fd ".*Test\.java" services/
  ```
- **Find Kubernetes YAML definitions**:
  ```bash
  fd -e yaml -e yml k8s/
  ```

---

## 4. 🧩 `ast-grep` (`sg`): Structural Code Pattern Matching

`ast-grep` parses code into syntax trees, allowing pattern searches that match code regardless of indentation, comments, or formatting.

### Useful Java Patterns
- **Find RedisTemplate operations**:
  ```bash
  ast-grep -l java -p 'redisTemplate.$$$'
  ```
- **Find all methods annotated with `@Transactional`**:
  ```bash
  ast-grep -l java -p '@Transactional public $$$ ($$$) { $$$ }'
  ```
- **Find all Spring GET endpoints**:
  ```bash
  ast-grep -l java -p '@GetMapping($$$) public $$$ ($$$) { $$$ }'
  ```

---

## 5. 🔍 `jq`: JSON Processing

Use `jq` to parse project manifests, Docker outputs, and build state files:

- **Check Graphify Node & Edge Counts**:
  ```bash
  jq '{nodes: .node_count, edges: .edge_count}' graphify-out/manifest.json
  ```
- **Inspect Service Build Status**:
  ```bash
  jq '.services' .build-state.json
  ```
