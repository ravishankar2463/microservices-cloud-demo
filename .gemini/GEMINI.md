# Gemini CLI & Antigravity Project Instructions

## Navigation & Discovery Hierarchy

When researching this codebase, always use the installed fast CLI tools in the following order:

1. **Architecture & Component Relationships**:
   - Check `graphify-out/graph.json` via `graphify query "<question>"`.
   - Trace connections between microservices or sagas with `graphify path` or `graphify explain`.
2. **Text & Regex Code Search**:
   - Use `rg` (ripgrep) instead of grep or findstr.
   - Target subdirectories (e.g. `services/order-service`, `common/`) to keep output focused.
3. **Locating Files**:
   - Use `fd` for rapid path and extension discovery.
4. **Structural AST Pattern Matching**:
   - Use `ast-grep -l java -p '<pattern>'` for Java syntax queries.
5. **No Local Model Daemons**:
   - Do not start background AI servers (OpenViking, Ollama, etc.) on this machine.
