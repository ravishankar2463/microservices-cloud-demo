package com.ravi.controlcenter.service;

import com.ravi.controlcenter.model.AwsEmulatorHealth;
import com.ravi.controlcenter.model.AwsEmulatorHealth.AwsServiceDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;

@Service
public class AwsEmulatorService {

    private static final Logger log = LoggerFactory.getLogger(AwsEmulatorService.class);

    private final String awsEmulatorUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final Map<String, ServiceMeta> SERVICE_CATALOG = Map.ofEntries(
        Map.entry("s3", new ServiceMeta("Amazon S3", "Storage", "Simple Storage Service (Object Storage)", "Stores customer invoice PDFs uploaded asynchronously by notification-service", "aws --endpoint-url=http://localhost:4566 s3 ls")),
        Map.entry("sqs", new ServiceMeta("Amazon SQS", "Messaging", "Simple Queue Service (Distributed Message Queuing)", "Buffers asynchronous saga events and handles Dead Letter Queues (DLQ)", "aws --endpoint-url=http://localhost:4566 sqs list-queues")),
        Map.entry("sns", new ServiceMeta("Amazon SNS", "Messaging", "Simple Notification Service (Pub/Sub Messaging)", "Broadcasts order lifecycle updates to multiple subscriber topics", "aws --endpoint-url=http://localhost:4566 sns list-topics")),
        Map.entry("dynamodb", new ServiceMeta("Amazon DynamoDB", "Database", "Managed NoSQL Key-Value & Document Database", "Stores high-throughput payment idempotency records and order read projections", "aws --endpoint-url=http://localhost:4566 dynamodb list-tables")),
        Map.entry("dynamodbstreams", new ServiceMeta("DynamoDB Streams", "Database / CDC", "Change Data Capture (CDC) stream for DynamoDB", "Captures real-time table mutations for audit replication", "aws --endpoint-url=http://localhost:4566 dynamodbstreams list-streams")),
        Map.entry("kinesis", new ServiceMeta("Amazon Kinesis", "Streaming", "High-throughput partitioned real-time data streaming", "Partitioned event streaming backbone alternative alongside Kafka", "aws --endpoint-url=http://localhost:4566 kinesis list-streams")),
        Map.entry("lambda", new ServiceMeta("AWS Lambda", "Compute", "Serverless event-driven compute engine", "Optional serverless function triggers on S3 invoice creation", "aws --endpoint-url=http://localhost:4566 lambda list-functions")),
        Map.entry("secretsmanager", new ServiceMeta("AWS Secrets Manager", "Security", "Secrets management and rotation service", "Optional centralized secrets storage for database passwords", "aws --endpoint-url=http://localhost:4566 secretsmanager list-secrets")),
        Map.entry("kms", new ServiceMeta("AWS KMS", "Security", "Key Management Service (Encryption Keys)", "Cryptographic envelope key management for payment tokenization", "aws --endpoint-url=http://localhost:4566 kms list-keys")),
        Map.entry("cloudwatch", new ServiceMeta("Amazon CloudWatch", "Monitoring", "Cloud metrics, alarms, and dashboards", "Unified cloud observability for simulated AWS resources", "aws --endpoint-url=http://localhost:4566 cloudwatch list-metrics")),
        Map.entry("logs", new ServiceMeta("CloudWatch Logs", "Monitoring", "Centralized log ingestion and query engine", "Log stream destination for AWS service audit trails", "aws --endpoint-url=http://localhost:4566 logs describe-log-groups")),
        Map.entry("iam", new ServiceMeta("AWS IAM", "Security & Identity", "Identity & Access Management", "Role and policy definition for simulated cloud components", "aws --endpoint-url=http://localhost:4566 iam list-roles")),
        Map.entry("apigateway", new ServiceMeta("Amazon API Gateway", "Networking", "Managed REST and WebSocket API gateway", "Alternative AWS API edge proxy", "aws --endpoint-url=http://localhost:4566 apigateway get-rest-apis")),
        Map.entry("stepfunctions", new ServiceMeta("AWS Step Functions", "Orchestration", "Visual state machine workflow orchestration", "Alternative orchestrator for multi-stage Saga compensation flows", "aws --endpoint-url=http://localhost:4566 stepfunctions list-state-machines")),
        Map.entry("events", new ServiceMeta("Amazon EventBridge", "Integration", "Serverless event bus", "Cloud-native event routing and filtering engine", "aws --endpoint-url=http://localhost:4566 events list-rules")),
        Map.entry("ses", new ServiceMeta("Amazon SES", "Messaging", "Simple Email Service for transactional emails", "Transactional email delivery emulator for invoice notifications", "aws --endpoint-url=http://localhost:4566 ses list-identities")),
        Map.entry("cloudformation", new ServiceMeta("AWS CloudFormation", "DevOps", "Infrastructure as Code (IaC) templating", "Declarative stack provisioning for mock AWS infrastructure", "aws --endpoint-url=http://localhost:4566 cloudformation list-stacks")),
        Map.entry("ssm", new ServiceMeta("AWS Systems Manager", "Management", "Parameter Store and operational management", "Hierarchical configuration parameters for cloud services", "aws --endpoint-url=http://localhost:4566 ssm describe-parameters"))
    );

    private record ServiceMeta(String displayName, String category, String description, String architectureUsage, String cliCommand) {}

    public AwsEmulatorService(
            @Value("${services.aws-emulator.url:http://localhost:4566}") String awsEmulatorUrl,
            ObjectMapper objectMapper) {
        this.awsEmulatorUrl = awsEmulatorUrl;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(3000));
        factory.setReadTimeout(Duration.ofMillis(4000));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public AwsEmulatorHealth getHealth() {
        String healthEndpoint = awsEmulatorUrl + "/_localstack/health";
        try {
            String json = restClient.get()
                    .uri(healthEndpoint)
                    .retrieve()
                    .body(String.class);

            if (json != null && !json.isBlank()) {
                return parseHealthResponse(json);
            }
        } catch (Exception e) {
            log.warn("Failed to reach AWS Emulator at {}: {}", healthEndpoint, e.getMessage());
        }

        return createOfflineHealth();
    }

    public AwsEmulatorHealth parseHealthResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String edition = root.path("edition").asText("community");
            String version = root.path("version").asText("unknown");

            JsonNode servicesNode = root.path("services");
            Map<String, String> rawServices = new LinkedHashMap<>();

            List<AwsServiceDetail> activeServices = new ArrayList<>();
            List<AwsServiceDetail> allServices = new ArrayList<>();

            int runningCount = 0;
            int availableCount = 0;
            int disabledCount = 0;

            if (servicesNode.isObject()) {
                for (Map.Entry<String, JsonNode> entry : servicesNode.properties()) {
                    String svcKey = entry.getKey();
                    String status = entry.getValue().asText("disabled").toLowerCase();
                    rawServices.put(svcKey, status);

                    boolean isActive = "running".equalsIgnoreCase(status) || "available".equalsIgnoreCase(status);
                    if ("running".equalsIgnoreCase(status)) {
                        runningCount++;
                        availableCount++;
                    } else if ("available".equalsIgnoreCase(status)) {
                        availableCount++;
                    } else {
                        disabledCount++;
                    }

                    ServiceMeta meta = SERVICE_CATALOG.get(svcKey);
                    String displayName = meta != null ? meta.displayName() : svcKey.toUpperCase();
                    String category = meta != null ? meta.category() : "Other";
                    String description = meta != null ? meta.description() : "AWS Service emulation";
                    String archUsage = meta != null ? meta.architectureUsage() : (isActive ? "Ready for cloud testing in ecosystem" : "Disabled in current profile");
                    String cli = meta != null ? meta.cliCommand() : "aws --endpoint-url=http://localhost:4566 " + svcKey;

                    AwsServiceDetail detail = new AwsServiceDetail(
                        displayName,
                        category,
                        status,
                        isActive,
                        description,
                        archUsage,
                        cli
                    );

                    allServices.add(detail);
                    if (isActive) {
                        activeServices.add(detail);
                    }
                }
            }

            // Sort active services so 'running' services appear before 'available'
            activeServices.sort(Comparator.comparing((AwsServiceDetail s) -> "running".equalsIgnoreCase(s.status()) ? 0 : 1)
                    .thenComparing(AwsServiceDetail::name));

            // Sort all services: active first, then alphabetically
            allServices.sort(Comparator.comparing((AwsServiceDetail s) -> s.active() ? 0 : 1)
                    .thenComparing(AwsServiceDetail::name));

            String status = availableCount > 0 ? "ONLINE" : "DEGRADED";
            String message = String.format("LocalStack %s v%s is running with %d active AWS service(s) (S3, SQS, SNS, DynamoDB, Kinesis).",
                    edition, version, availableCount);

            return new AwsEmulatorHealth(
                edition,
                version,
                status,
                allServices.size(),
                availableCount,
                runningCount,
                disabledCount,
                activeServices,
                allServices,
                "http://localhost:4566",
                rawServices,
                message
            );
        } catch (Exception e) {
            log.error("Error parsing LocalStack health JSON: {}", e.getMessage());
            return createOfflineHealth();
        }
    }

    private AwsEmulatorHealth createOfflineHealth() {
        return new AwsEmulatorHealth(
            "community",
            "offline",
            "OFFLINE",
            0,
            0,
            0,
            0,
            List.of(),
            List.of(),
            "http://localhost:4566",
            Map.of(),
            "AWS Emulator (LocalStack) is currently unreachable on port 4566."
        );
    }
}
