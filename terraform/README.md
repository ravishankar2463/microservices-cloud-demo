# LocalStack AWS Infrastructure-as-Code (Terraform)

This directory provides automated Terraform provisioning for all AWS resources required by the microservices ecosystem. It runs 100% locally against the LocalStack emulator on `http://localhost:4566` with zero AWS account, zero credit card, and zero cloud sign-in required.

---

## 🏗️ Provisioned Resources

| Module | Resource | Type | Purpose |
| :--- | :--- | :--- | :--- |
| **`messaging`** | `order-events-topic` | AWS SNS | Asynchronous broadcast of order events across bounded contexts |
| **`messaging`** | `order-events-queue` | AWS SQS | Subscribed queue for `notification-service` order confirmation handling |
| **`messaging`** | `inventory-events-queue` | AWS SQS | Subscribed queue for `inventory-service` stock reservation workflows |
| **`messaging`** | `order-events-dlq` | AWS SQS | Dead Letter Queue (DLQ) for poisoned messages after 3 retries |
| **`storage`** | `order-invoices-bucket` | AWS S3 | Object storage bucket for generated invoice PDFs |
| **`storage`** | `inventory-items` | AWS DynamoDB | High-throughput NoSQL item store for stock audit and balance checks |

---

## 🚀 Usage

```bash
# 1. Initialize Terraform provider
terraform init

# 2. Plan resource changes
terraform plan

# 3. Apply infrastructure to LocalStack
terraform apply -auto-approve
```
