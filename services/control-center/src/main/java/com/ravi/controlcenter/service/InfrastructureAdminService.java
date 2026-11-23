package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.InfraNodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class InfrastructureAdminService {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureAdminService.class);
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");
    private static final Path SERVICEACCOUNT_TOKEN_PATH = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");
    private static final Path SERVICEACCOUNT_NS_PATH = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
    private static final Path SERVICEACCOUNT_CA_PATH = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = createInClusterHttpClient();
    private final List<InfraNodeInfo> lastKnownK8sPods = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<InfraNodeInfo> lastKnownDockerContainers = new java.util.concurrent.CopyOnWriteArrayList<>();

    public List<InfraNodeInfo> getNodes(String platform) {
        String targetPlatform = (platform == null || platform.isBlank()) ? autoDetectPlatform() : platform.toLowerCase();

        if ("k8s".equals(targetPlatform) || "kubernetes".equals(targetPlatform)) {
            return getKubernetesPods();
        } else {
            return getDockerContainers();
        }
    }

    public String autoDetectPlatform() {
        if (isInCluster()) {
            return "k8s";
        }
        try {
            Process process = new ProcessBuilder("kubectl", "get", "pods", "-n", "microservices-demo", "--no-headers")
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return "k8s";
            }
        } catch (Exception e) {
            log.debug("Kubernetes not active: {}", e.getMessage());
        }
        return "docker";
    }

    public boolean isInCluster() {
        return Files.exists(SERVICEACCOUNT_TOKEN_PATH);
    }

    private String getInClusterNamespace() {
        try {
            if (Files.exists(SERVICEACCOUNT_NS_PATH)) {
                return Files.readString(SERVICEACCOUNT_NS_PATH).trim();
            }
        } catch (Exception ignored) {}
        return "microservices-demo";
    }

    private String getInClusterToken() {
        try {
            if (Files.exists(SERVICEACCOUNT_TOKEN_PATH)) {
                return Files.readString(SERVICEACCOUNT_TOKEN_PATH).trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public List<InfraNodeInfo> getKubernetesPods() {
        if (isInCluster()) {
            try {
                String ns = getInClusterNamespace();
                String token = getInClusterToken();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://kubernetes.default.svc/api/v1/namespaces/" + ns + "/pods"))
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null) {
                    List<InfraNodeInfo> parsed = parseKubernetesJson(resp.body());
                    if (!parsed.isEmpty()) {
                        lastKnownK8sPods.clear();
                        lastKnownK8sPods.addAll(parsed);
                        return parsed;
                    }
                }
                log.warn("In-cluster k8s API returned status {}", resp.statusCode());
            } catch (Exception e) {
                log.warn("Failed in-cluster Kubernetes query: {}", e.getMessage());
            }
        }

        // Host / CLI fallback
        try {
            Process process = new ProcessBuilder("kubectl", "get", "pods", "-n", "microservices-demo", "-o", "json")
                .redirectErrorStream(true)
                .start();

            String jsonOutput = readProcessOutput(process, 6);
            if (jsonOutput != null && !jsonOutput.isBlank() && !jsonOutput.startsWith("Execution timed out")) {
                List<InfraNodeInfo> parsed = parseKubernetesJson(jsonOutput);
                if (!parsed.isEmpty()) {
                    lastKnownK8sPods.clear();
                    lastKnownK8sPods.addAll(parsed);
                    return parsed;
                }
            }
        } catch (Exception e) {
            log.error("Failed to query Kubernetes pods via CLI: {}", e.getMessage());
        }

        if (!lastKnownK8sPods.isEmpty()) {
            log.debug("Returning last known {} pods to prevent UI flicker", lastKnownK8sPods.size());
            return new ArrayList<>(lastKnownK8sPods);
        }
        return Collections.emptyList();
    }

    public List<InfraNodeInfo> parseKubernetesJson(String jsonOutput) {
        List<InfraNodeInfo> nodes = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonOutput);
            JsonNode items = root.path("items");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    String name = item.path("metadata").path("name").asText("unknown");
                    String phase = item.path("status").path("phase").asText("Unknown");

                    JsonNode containerStatuses = item.path("status").path("containerStatuses");
                    int readyCount = 0;
                    int totalCount = containerStatuses.isArray() ? containerStatuses.size() : 1;
                    int restarts = 0;
                    String failureReason = "";
                    String failureMessage = "";
                    int exitCode = 0;
                    String state = phase.toUpperCase();

                    if (containerStatuses.isArray() && !containerStatuses.isEmpty()) {
                        for (JsonNode cs : containerStatuses) {
                            if (cs.path("ready").asBoolean(false)) {
                                readyCount++;
                            }
                            restarts += cs.path("restartCount").asInt(0);

                            JsonNode stateNode = cs.path("state");
                            JsonNode waiting = stateNode.path("waiting");
                            JsonNode currTerminated = stateNode.path("terminated");

                            if (!waiting.isMissingNode()) {
                                failureReason = waiting.path("reason").asText("");
                                failureMessage = waiting.path("message").asText("");
                                state = failureReason.toUpperCase();
                                // Check lastState if currently waiting (e.g., CrashLoopBackOff) to see previous exit code
                                JsonNode lastTerm = cs.path("lastState").path("terminated");
                                if (!lastTerm.isMissingNode()) {
                                    exitCode = lastTerm.path("exitCode").asInt(0);
                                    if (failureReason.isBlank()) {
                                        failureReason = lastTerm.path("reason").asText("");
                                    }
                                }
                            } else if (!currTerminated.isMissingNode()) {
                                exitCode = currTerminated.path("exitCode").asInt(0);
                                failureReason = currTerminated.path("reason").asText("");
                                failureMessage = currTerminated.path("message").asText("");
                                state = failureReason.isBlank() ? "TERMINATED" : failureReason.toUpperCase();
                            }
                        }
                    }

                    String statusText;
                    if (readyCount == totalCount && "Running".equalsIgnoreCase(phase)) {
                        state = "RUNNING";
                        statusText = "Running";
                        exitCode = 0;
                        failureReason = "";
                        failureMessage = "";
                    } else if (readyCount < totalCount && "Running".equalsIgnoreCase(phase)) {
                        state = "NOT_READY";
                        statusText = failureReason.isBlank() ? ("Running (" + readyCount + "/" + totalCount + " Ready)") : failureReason;
                    } else if ("Pending".equalsIgnoreCase(phase)) {
                        state = "PENDING";
                        statusText = failureReason.isBlank() ? "Pending" : failureReason;
                    } else if (state.contains("CRASH") || state.contains("ERROR") || exitCode != 0) {
                        state = "CRASH_LOOP";
                        statusText = failureReason.isBlank() ? "CrashLoopBackOff" : failureReason;
                    } else {
                        statusText = failureReason.isBlank() ? phase : failureReason;
                    }

                    String readyStatus = readyCount + "/" + totalCount;
                    String age = calculateAge(item.path("metadata").path("creationTimestamp").asText(""));

                    nodes.add(new InfraNodeInfo(
                        name,
                        name,
                        "KUBERNETES",
                        state,
                        statusText,
                        restarts,
                        age,
                        failureReason,
                        failureMessage,
                        exitCode,
                        readyStatus
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Kubernetes JSON: {}", e.getMessage());
        }
        return nodes;
    }

    public List<InfraNodeInfo> getDockerContainers() {
        List<InfraNodeInfo> nodes = new ArrayList<>();
        try {
            Process process = new ProcessBuilder("docker", "ps", "-a", "--format", "{{json .}}")
                .redirectErrorStream(true)
                .start();

            List<String> lines = readProcessLines(process, 5);
            for (String line : lines) {
                if (line.isBlank() || !line.trim().startsWith("{")) continue;
                try {
                    JsonNode node = objectMapper.readTree(line);
                    String name = node.path("Names").asText("");
                    String rawState = node.path("State").asText("unknown").toLowerCase();
                    String status = node.path("Status").asText("");
                    String id = node.path("ID").asText("");
                    String runningFor = node.path("RunningFor").asText("");

                    int exitCode = 0;
                    String failureReason = "";
                    String state = "RUNNING";

                    if ("running".equalsIgnoreCase(rawState)) {
                        state = "RUNNING";
                    } else if ("exited".equalsIgnoreCase(rawState)) {
                        state = "EXITED";
                        if (status.contains("(") && status.contains(")")) {
                            try {
                                String codeStr = status.substring(status.indexOf('(') + 1, status.indexOf(')'));
                                exitCode = Integer.parseInt(codeStr);
                                if (exitCode != 0) {
                                    failureReason = "Container exited with non-zero exit code " + exitCode;
                                    state = "ERROR";
                                }
                            } catch (Exception ignored) {}
                        }
                    } else if ("restarting".equalsIgnoreCase(rawState)) {
                        state = "CRASH_LOOP";
                        failureReason = "CrashLoopRestarting";
                    }

                    nodes.add(new InfraNodeInfo(
                        id,
                        name,
                        "DOCKER",
                        state,
                        status,
                        0,
                        runningFor,
                        failureReason,
                        failureReason,
                        exitCode,
                        "running".equalsIgnoreCase(rawState) ? "1/1" : "0/1"
                    ));
                } catch (Exception ex) {
                    log.debug("Skipping unparseable docker line: {}", ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to query Docker containers: {}", e.getMessage());
        }
        return nodes;
    }

    public String getLogs(String platform, String name, int tail) {
        if (name == null || !SAFE_NAME_PATTERN.matcher(name).matches()) {
            return "Error: Invalid container/pod identifier.";
        }
        int clampedTail = Math.clamp(tail, 10, 1000);
        String targetPlatform = (platform == null || platform.isBlank()) ? autoDetectPlatform() : platform.toLowerCase();

        if ("k8s".equals(targetPlatform) || "kubernetes".equals(targetPlatform)) {
            // In-cluster attempt
            if (isInCluster()) {
                try {
                    String ns = getInClusterNamespace();
                    String token = getInClusterToken();
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://kubernetes.default.svc/api/v1/namespaces/" + ns + "/pods/" + name + "/log?tailLines=" + clampedTail))
                        .header("Authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                        return resp.body();
                    }
                    // Try previous
                    HttpRequest prevReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://kubernetes.default.svc/api/v1/namespaces/" + ns + "/pods/" + name + "/log?tailLines=" + clampedTail + "&previous=true"))
                        .header("Authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                    HttpResponse<String> prevResp = httpClient.send(prevReq, HttpResponse.BodyHandlers.ofString());
                    if (prevResp.statusCode() == 200 && prevResp.body() != null && !prevResp.body().isBlank()) {
                        return "[PREVIOUS CRASH LOGS]:\n" + prevResp.body();
                    }
                } catch (Exception e) {
                    log.warn("In-cluster log fetch error: {}", e.getMessage());
                }
            }

            // CLI fallback
            try {
                ProcessBuilder pb = new ProcessBuilder("kubectl", "logs", "--tail", String.valueOf(clampedTail), "-n", "microservices-demo", name);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String output = readProcessOutput(process, 6);

                if (output == null || output.isBlank()) {
                    Process prevProcess = new ProcessBuilder("kubectl", "logs", "--tail", String.valueOf(clampedTail), "--previous", "-n", "microservices-demo", name)
                        .redirectErrorStream(true)
                        .start();
                    String prevOutput = readProcessOutput(prevProcess, 5);
                    if (prevOutput != null && !prevOutput.isBlank()) {
                        return "[PREVIOUS CRASH LOGS]:\n" + prevOutput;
                    }
                }
                return (output != null && !output.isBlank()) ? output : "No logs available for " + name;
            } catch (Exception e) {
                return "Failed to retrieve logs: " + e.getMessage();
            }
        }

        // Docker
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "logs", "--tail", String.valueOf(clampedTail), name);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = readProcessOutput(process, 6);
            return (output != null && !output.isBlank()) ? output : "No logs available for " + name;
        } catch (Exception e) {
            return "Failed to retrieve Docker logs: " + e.getMessage();
        }
    }

    public String getDiagnostics(String platform, String name) {
        if (name == null || !SAFE_NAME_PATTERN.matcher(name).matches()) {
            return "Error: Invalid container/pod identifier.";
        }
        String targetPlatform = (platform == null || platform.isBlank()) ? autoDetectPlatform() : platform.toLowerCase();

        if ("k8s".equals(targetPlatform) || "kubernetes".equals(targetPlatform)) {
            if (isInCluster()) {
                try {
                    String ns = getInClusterNamespace();
                    String token = getInClusterToken();
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://kubernetes.default.svc/api/v1/namespaces/" + ns + "/events?fieldSelector=involvedObject.name%3D" + name))
                        .header("Authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200 && resp.body() != null) {
                        return formatEventsJson(resp.body(), name);
                    }
                } catch (Exception e) {
                    log.warn("In-cluster events query error: {}", e.getMessage());
                }
            }

            try {
                ProcessBuilder pb = new ProcessBuilder("kubectl", "describe", "pod", name, "-n", "microservices-demo");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                return readProcessOutput(process, 8);
            } catch (Exception e) {
                return "Failed to run diagnostics: " + e.getMessage();
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "inspect", name);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return readProcessOutput(process, 8);
        } catch (Exception e) {
            return "Failed to run diagnostics: " + e.getMessage();
        }
    }

    private String formatEventsJson(String eventsJson, String podName) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== IN-CLUSTER KUBERNETES POD DIAGNOSTICS FOR: ").append(podName).append(" ===\n\n");
        try {
            JsonNode root = objectMapper.readTree(eventsJson);
            JsonNode items = root.path("items");
            if (items.isArray() && !items.isEmpty()) {
                sb.append(String.format("%-12s %-18s %-12s %s\n", "TYPE", "REASON", "AGE", "MESSAGE"));
                sb.append("--------------------------------------------------------------------------------\n");
                for (JsonNode ev : items) {
                    String type = ev.path("type").asText("Normal");
                    String reason = ev.path("reason").asText("");
                    String message = ev.path("message").asText("");
                    String age = calculateAge(ev.path("lastTimestamp").asText(""));
                    sb.append(String.format("%-12s %-18s %-12s %s\n", type, reason, age, message));
                }
            } else {
                sb.append("No active error events recorded for this pod in namespace.");
            }
        } catch (Exception e) {
            sb.append(eventsJson);
        }
        return sb.toString();
    }

    private HttpClient createInClusterHttpClient() {
        try {
            if (Files.exists(SERVICEACCOUNT_CA_PATH)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate caCert;
                try (var is = Files.newInputStream(SERVICEACCOUNT_CA_PATH)) {
                    caCert = (X509Certificate) cf.generateCertificate(is);
                }
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null, null);
                ks.setCertificateEntry("k8s-ca", caCert);

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

                return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            }

            return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        } catch (Exception e) {
            log.warn("Failed to configure cluster CA SSL context: {}", e.getMessage());
            return HttpClient.newHttpClient();
        }
    }

    private String readProcessOutput(Process process, int timeoutSeconds) {
        StringBuilder sb = new StringBuilder();
        try {
            CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                } catch (Exception e) {
                    log.debug("Process stream read error: {}", e.getMessage());
                }
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                readFuture.cancel(true);
                return "Execution timed out after " + timeoutSeconds + " seconds.";
            }
            readFuture.get(2, TimeUnit.SECONDS);
            return sb.toString();
        } catch (Exception e) {
            log.warn("Process execution error: {}", e.getMessage());
            return "";
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private List<String> readProcessLines(Process process, int timeoutSeconds) {
        List<String> lines = new ArrayList<>();
        try {
            CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (Exception e) {
                    log.debug("Process line read error: {}", e.getMessage());
                }
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                readFuture.cancel(true);
                return lines;
            }
            readFuture.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Process line read exception: {}", e.getMessage());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return lines;
    }

    private String calculateAge(String timestampStr) {
        if (timestampStr == null || timestampStr.isBlank()) return "N/A";
        try {
            java.time.Instant created = java.time.Instant.parse(timestampStr);
            long seconds = java.time.Duration.between(created, java.time.Instant.now()).getSeconds();
            if (seconds < 60) return seconds + "s";
            if (seconds < 3600) return (seconds / 60) + "m";
            return (seconds / 3600) + "h";
        } catch (Exception e) {
            return "N/A";
        }
    }
}
