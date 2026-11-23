output "order_events_topic_arn" {
  value = aws_sns_topic.order_events.arn
}

output "order_events_queue_url" {
  value = aws_sqs_queue.order_events.url
}

output "inventory_events_queue_url" {
  value = aws_sqs_queue.inventory_events.url
}

output "order_events_dlq_url" {
  value = aws_sqs_queue.dlq.url
}
