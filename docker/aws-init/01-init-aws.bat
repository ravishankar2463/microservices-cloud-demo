@echo off
REM ==============================================================================
REM Windows Batch script to initialize Local AWS Emulator resources
REM Requires AWS CLI installed locally or running within container.
REM ==============================================================================

set AWS_ACCESS_KEY_ID=test
set AWS_SECRET_ACCESS_KEY=test
set AWS_DEFAULT_REGION=us-east-1
set ENDPOINT_URL=http://localhost:4566

echo [INFO] Creating S3 Buckets...
aws --endpoint-url=%ENDPOINT_URL% s3 mb s3://order-invoices-bucket
aws --endpoint-url=%ENDPOINT_URL% s3 mb s3://email-templates

echo [INFO] Creating SQS Queues...
aws --endpoint-url=%ENDPOINT_URL% sqs create-queue --queue-name order-events-dlq
aws --endpoint-url=%ENDPOINT_URL% sqs create-queue --queue-name order-events-queue
aws --endpoint-url=%ENDPOINT_URL% sqs create-queue --queue-name inventory-events-queue

echo [INFO] Creating SNS Topic...
aws --endpoint-url=%ENDPOINT_URL% sns create-topic --name order-events-topic

echo [INFO] Creating DynamoDB Table: inventory-items...
aws --endpoint-url=%ENDPOINT_URL% dynamodb create-table --table-name inventory-items --attribute-definitions AttributeName=productId,AttributeType=S --key-schema AttributeName=productId,KeyType=HASH --billing-mode PAY_PER_REQUEST

echo [SUCCESS] Local AWS Emulator initialized successfully!
