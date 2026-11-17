package com.ravi.controlcenter.model;

import java.util.List;
import java.util.Map;

/**
 * Point-in-time infrastructure state snapshot across PostgreSQL, Redis, Kafka, and AWS LocalStack.
 */
public record InfrastructureSnapshot(
    DatabaseState database,
    RedisState redis,
    KafkaState kafka,
    AwsState aws
) {
    public record DatabaseState(
        String databaseName,
        String tableName,
        int rowCount,
        String latestRecordStatus,
        String outboxStatus
    ) {}

    public record RedisState(
        int stockQuantity,
        boolean lockHeld,
        String lockKey,
        long lockTtlMs
    ) {}

    public record KafkaState(
        String topic,
        long partitionOffset,
        String lastEventKey
    ) {}

    public record AwsState(
        String s3Bucket,
        int objectCount,
        boolean invoiceFound,
        String invoiceKey
    ) {}
}
