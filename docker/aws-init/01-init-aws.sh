#!/usr/bin/env bash
set -eo pipefail

echo "======================================================================"
echo "Initializing Local AWS Emulator Resources (SQS, SNS, S3, DynamoDB)..."
echo "Zero sign-in required, running 100% locally."
echo "======================================================================"

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
ENDPOINT_URL="http://localhost:4566"

# 1. Create S3 Buckets
echo "--> Creating S3 Buckets..."
awslocal s3 mb s3://order-invoices-bucket || aws --endpoint-url=${ENDPOINT_URL} s3 mb s3://order-invoices-bucket
awslocal s3 mb s3://email-templates || aws --endpoint-url=${ENDPOINT_URL} s3 mb s3://email-templates

# 2. Create SQS Queues
echo "--> Creating SQS Queues (including Dead Letter Queue)..."
awslocal sqs create-queue --queue-name order-events-dlq || aws --endpoint-url=${ENDPOINT_URL} sqs create-queue --queue-name order-events-dlq
awslocal sqs create-queue --queue-name order-events-queue || aws --endpoint-url=${ENDPOINT_URL} sqs create-queue --queue-name order-events-queue
awslocal sqs create-queue --queue-name inventory-events-queue || aws --endpoint-url=${ENDPOINT_URL} sqs create-queue --queue-name inventory-events-queue

# 3. Create SNS Topics
echo "--> Creating SNS Topics..."
ORDER_TOPIC_ARN=$(awslocal sns create-topic --name order-events-topic --output text --query 'TopicArn' 2>/dev/null || aws --endpoint-url=${ENDPOINT_URL} sns create-topic --name order-events-topic --output text --query 'TopicArn')
echo "Created SNS Topic ARN: ${ORDER_TOPIC_ARN}"

# 4. Subscribe SQS Queues to SNS Topic (Fan-Out Pattern)
echo "--> Subscribing SQS Queues to SNS Topic..."
ORDER_QUEUE_ARN=$(awslocal sqs get-queue-attributes --queue-url ${ENDPOINT_URL}/000000000000/order-events-queue --attribute-names QueueArn --output text --query 'Attributes.QueueArn' 2>/dev/null || aws --endpoint-url=${ENDPOINT_URL} sqs get-queue-attributes --queue-url ${ENDPOINT_URL}/000000000000/order-events-queue --attribute-names QueueArn --output text --query 'Attributes.QueueArn')

awslocal sns subscribe --topic-arn "${ORDER_TOPIC_ARN}" --protocol sqs --notification-endpoint "${ORDER_QUEUE_ARN}" || aws --endpoint-url=${ENDPOINT_URL} sns subscribe --topic-arn "${ORDER_TOPIC_ARN}" --protocol sqs --notification-endpoint "${ORDER_QUEUE_ARN}"

# 5. Create DynamoDB Table for Inventory Service
echo "--> Creating DynamoDB Table: inventory-items..."
awslocal dynamodb create-table \
    --table-name inventory-items \
    --attribute-definitions AttributeName=productId,AttributeType=S \
    --key-schema AttributeName=productId,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST || \
aws --endpoint-url=${ENDPOINT_URL} dynamodb create-table \
    --table-name inventory-items \
    --attribute-definitions AttributeName=productId,AttributeType=S \
    --key-schema AttributeName=productId,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST

echo "======================================================================"
echo "Local AWS Emulator initialization completed successfully!"
echo "======================================================================"
