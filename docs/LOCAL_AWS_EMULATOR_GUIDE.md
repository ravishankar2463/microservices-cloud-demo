# Local AWS Emulator Guide (Zero Sign-in & Local Testing)

This guide explains how to test and develop cloud-native microservices locally without an AWS account, credit card, or active internet connection.

---

## 1. Overview of the Local AWS Emulator

The local AWS emulator simulates the official AWS cloud services directly on your workstation inside Docker:

- **Supported Emulated Services**:
  - **AWS SQS**: Asynchronous message queues and Dead Letter Queues (DLQ).
  - **AWS SNS**: Pub/Sub topic event fan-out to multiple subscriber queues.
  - **AWS S3**: Object storage for invoices, receipts, and templates.
  - **AWS DynamoDB**: High-speed NoSQL document storage.
- **Port**: `http://localhost:4566`
- **Authentication**: **No sign-in required**. Uses dummy credentials (`accessKey=test`, `secretKey=test`, `region=us-east-1`).

---

## 2. Quickstart: Starting the Emulator

Navigate to the `docker/` folder and start the emulator:

```bash
# Start the AWS emulator, PostgreSQL, and Redis in the background
cd docker
docker compose up -d aws-emulator order-db inventory-redis
```

### Checking Emulator Health
Open your browser or run cURL:

```bash
curl http://localhost:4566/_localstack/health
```

Expected JSON response:
```json
{
  "services": {
    "dynamodb": "running",
    "s3": "running",
    "sns": "running",
    "sqs": "running"
  }
}
```

---

## 3. Initializing Cloud Resources

The emulator automatically executes `docker/aws-init/01-init-aws.sh` on startup. 
Alternatively, you can run the initialization script manually at any time:

### On Linux / macOS / Git Bash:
```bash
chmod +x docker/aws-init/01-init-aws.sh
./docker/aws-init/01-init-aws.sh
```

### On Windows Command Prompt / PowerShell:
```cmd
docker\aws-init\01-init-aws.bat
```

---

## 4. Useful AWS CLI Commands (Using the Emulator)

You can interact with the local emulator using the standard AWS CLI by supplying the `--endpoint-url` flag:

### S3 Commands
```bash
# List all buckets
aws --endpoint-url=http://localhost:4566 s3 ls

# Upload a test file to the invoices bucket
aws --endpoint-url=http://localhost:4566 s3 cp test-invoice.pdf s3://order-invoices-bucket/

# List objects inside the bucket
aws --endpoint-url=http://localhost:4566 s3 ls s3://order-invoices-bucket/
```

### SQS Commands
```bash
# List all queues
aws --endpoint-url=http://localhost:4566 sqs list-queues

# Send a manual test message to order-events-queue
aws --endpoint-url=http://localhost:4566 sqs send-message \
    --queue-url http://localhost:4566/000000000000/order-events-queue \
    --message-body '{"orderId":"11111111-2222-3333-4444-555555555555","customerId":"CUST-01"}'

# Receive and inspect messages from the queue
aws --endpoint-url=http://localhost:4566 sqs receive-message \
    --queue-url http://localhost:4566/000000000000/order-events-queue
```

### SNS Commands
```bash
# List topics
aws --endpoint-url=http://localhost:4566 sns list-topics

# Publish a broadcast message to order-events-topic
aws --endpoint-url=http://localhost:4566 sns publish \
    --topic-arn arn:aws:sns:us-east-1:000000000000:order-events-topic \
    --message "Order Placed Notification"
```

### DynamoDB Commands
```bash
# List tables
aws --endpoint-url=http://localhost:4566 dynamodb list-tables

# Scan items in inventory-items table
aws --endpoint-url=http://localhost:4566 dynamodb scan --table-name inventory-items
```

---

## 5. How Spring Boot Connects to the Emulator

Each microservice includes the `common-aws` module, which configures AWS clients automatically based on `application.yaml`:

```yaml
aws:
  endpoint: ${AWS_ENDPOINT_URL:http://localhost:4566}
  region: ${AWS_REGION:us-east-1}
  emulator-mode: true
```

When `emulator-mode: true`, the application:
1. Overrides the service endpoint URI to `http://localhost:4566` (or `http://aws-emulator:4566` in Docker/K8s).
2. Enables `pathStyleAccessEnabled(true)` for S3.
3. Injects static mock credentials (`test`/`test`), eliminating any prompt or need to sign in to an AWS account.
