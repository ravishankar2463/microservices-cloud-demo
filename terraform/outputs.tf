output "order_events_topic_arn" {
  description = "ARN of the SNS topic for order events fan-out"
  value       = module.messaging.order_events_topic_arn
}

output "order_events_queue_url" {
  description = "URL of the primary order events SQS queue"
  value       = module.messaging.order_events_queue_url
}

output "inventory_events_queue_url" {
  description = "URL of the inventory reservation SQS queue"
  value       = module.messaging.inventory_events_queue_url
}

output "order_events_dlq_url" {
  description = "URL of the Dead Letter Queue (DLQ) for poisoned saga messages"
  value       = module.messaging.order_events_dlq_url
}

output "invoice_bucket_id" {
  description = "S3 bucket ID storing PDF order invoices"
  value       = module.storage.invoice_bucket_id
}

output "inventory_table_name" {
  description = "DynamoDB table name for high-throughput inventory reservations"
  value       = module.storage.inventory_table_name
}
