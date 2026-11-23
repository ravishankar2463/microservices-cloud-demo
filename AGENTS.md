# Codebase Navigation & Agent Operating Guidelines

This repository (`aws-demo`) contains a distributed, cloud-native Java 25 microservice ecosystem with Hexagonal Architecture, Saga Choreography, Event-Driven Architecture (EDA), and local infrastructure emulation.

To ensure fast, accurate, and token-efficient analysis, all AI agents (Antigravity, Gemini CLI, Claude, Codex, etc.) must follow these navigation and tool selection protocols.

---

## 🛠️ Tooling Hierarchy & Selection Protocol

When answering questions, diagnosing issues, or planning architectural changes, select the most specific tool according to this hierarchy:

| Task / Query Intent | Primary Tool | Fallback / Complementary | Command Syntax |
| :--- | :--- | :--- | :--- |
| **Architectural / Class Dependencies** | **Graphify** | `ast-grep` | `graphify query "<topic>"`<br>`graphify path "<A>" "<B>"` |
| **Node / Symbol Deep Dive** | **Graphify** | `rg` | `graphify explain "<NodeId>"` |
| **Literal Text & Regex Search** | **Ripgrep (`rg`)** | `fd` | `rg "<pattern>" services/` |
| **File / Directory Discovery** | **`fd`** | `Get-ChildItem` | `fd "<filename>" services/` |
| **Structural Java Syntax Search** | **`ast-grep` (`sg`)**| `rg` | `ast-grep -p "$$$" -l java` |
| **JSON Data Processing** | **`jq`** | PowerShell ConvertFrom-Json | `jq '.node_count' graphify-out/manifest.json` |

> ⚠️ **Resource Policy**: Do NOT launch local model servers, background LLMs, or local embedding servers (e.g., OpenViking server, local Ollama) on this PC. All repository analysis relies strictly on AST parsing, local indexing, and standard CLI utilities.

---

## 1. Graphify (`graphify`)

An AST-based knowledge graph is pre-indexed in `graphify-out/graph.json` and visualizable via `graphify-out/graph.html`.

### Key Commands for Agents
- **Broad Semantic / Architecture Query**:
  ```bash
  graphify query "order saga"
  graphify query "inventory reservation" --budget 4000
  ```
- **Find Relationship / Shortest Path Between Components**:
  ```bash
  graphify path "<SourceNodeId>" "<TargetNodeId>"
  ```
- **Explain Symbol Connections & Callers**:
  ```bash
  graphify explain "<NodeId>" --all
  ```
- **Update Graph After Code Changes**:
  ```powershell
  .\scripts\update-graph.ps1
  # Or: graphify update . --no-cluster && graphify export html
  ```

---

## 2. Ripgrep (`rg`)

Fast, multithreaded regex search across the entire project. Respects `.gitignore`.

### Common Invocations in this Java Repo
- **Find Class or Interface Declarations**:
  ```bash
  rg "public (class|interface|record|enum) \w+" services/
  ```
- **Find Spring Bean Annotations**:
  ```bash
  rg "@(Service|Component|Repository|RestController)" services/
  ```
- **Find Event Listeners (Kafka / SQS)**:
  ```bash
  rg "@(KafkaListener|SqsListener)" services/
  ```
- **Find Domain Events or Sealed Interfaces**:
  ```bash
  rg "sealed interface.*Event" services/ common/
  ```
- **Search only in specific file extensions**:
  ```bash
  rg "outbox" -g "*.java"
  rg "LocalStack" -g "*.{yml,yaml,properties}"
  ```

---

## 3. Fast File Discovery (`fd`)

Instant file and path lookup.

### Common Invocations
- Find all `pom.xml` files: `fd "pom.xml"`
- Find entities or repositories: `fd ".*(Entity|Repository)\.java" services/`
- Find Kubernetes YAMLs: `fd -e yaml -e yml k8s/`
- Find PowerShell and Shell scripts: `fd -e ps1 -e sh`

---

## 4. Structural Code Search (`ast-grep` / `sg`)

AST-aware code search that ignores whitespace, formatting, and comments.

### Common Invocations
- Match method calls:
  ```bash
  ast-grep -l java -p 'redisTemplate.$$$'
  ```
- Match Spring endpoints:
  ```bash
  ast-grep -l java -p '@PostMapping($$$) public $$$ ($$$) { $$$ }'
  ```

---

## 5. JSON Processing (`jq`)

Parse and filter CLI and build outputs.

### Common Invocations
- Inspect graph manifest:
  ```bash
  jq '{node_count, edge_count}' graphify-out/manifest.json
  ```
- Inspect build state:
  ```bash
  jq '.services' .build-state.json
  ```
