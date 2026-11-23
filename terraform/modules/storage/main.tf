resource "aws_s3_bucket" "invoices" {
  bucket        = "order-invoices-bucket"
  force_destroy = true
}

resource "aws_dynamodb_table" "inventory_items" {
  name           = "inventory-items"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "productId"

  attribute {
    name = "productId"
    type = "S"
  }

  tags = {
    Environment = var.environment
    Service     = "inventory-service"
  }
}
