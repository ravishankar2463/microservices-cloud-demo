output "invoice_bucket_id" {
  value = aws_s3_bucket.invoices.id
}

output "inventory_table_name" {
  value = aws_dynamodb_table.inventory_items.name
}
