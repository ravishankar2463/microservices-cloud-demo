resource "aws_sqs_queue" "dlq" {
  name                      = var.order_events_dlq
  message_retention_seconds = 1209600 # 14 days
}

resource "aws_sqs_queue" "order_events" {
  name                      = "order-events-queue"
  delay_seconds             = 0
  max_message_size          = 262144
  message_retention_seconds = 86400

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "inventory_events" {
  name                      = "inventory-events-queue"
  delay_seconds             = 0
  max_message_size          = 262144
  message_retention_seconds = 86400
}

resource "aws_sns_topic" "order_events" {
  name = "order-events-topic"
}

resource "aws_sns_topic_subscription" "order_sub" {
  topic_arn = aws_sns_topic.order_events.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.order_events.arn
}

resource "aws_sns_topic_subscription" "inventory_sub" {
  topic_arn = aws_sns_topic.order_events.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.inventory_events.arn
}
