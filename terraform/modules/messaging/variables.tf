variable "environment" {
  type    = string
  default = "dev"
}

variable "order_events_dlq" {
  type    = string
  default = "order-events-dlq"
}
