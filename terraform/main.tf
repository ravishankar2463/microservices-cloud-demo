# ==============================================================================
# Root Module: LocalStack AWS Cloud Infrastructure
# Orchestrates Messaging (SQS/SNS) and Storage (S3/DynamoDB) for Decoupled Microservices
# ==============================================================================

module "messaging" {
  source           = "./modules/messaging"
  environment      = var.environment
  order_events_dlq = "order-events-dlq"
}

module "storage" {
  source      = "./modules/storage"
  environment = var.environment
}
