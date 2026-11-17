package com.ravi.common.aws.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * Spring Auto-Configuration producing AWS SDK v2 client beans.
 * Automatically wires Local AWS Emulator endpoint overrides and dummy credentials
 * so developers can test locally without requiring an AWS account or signing in.
 */
@AutoConfiguration
@EnableConfigurationProperties(AwsEmulatorProperties.class)
public class AwsClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AwsClientConfiguration.class);

    private final AwsEmulatorProperties properties;

    public AwsClientConfiguration(AwsEmulatorProperties properties) {
        this.properties = properties;
    }

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
        );
    }

    private Region region() {
        return Region.of(properties.getRegion());
    }

    @Bean
    @ConditionalOnMissingBean
    public SqsClient sqsClient() {
        log.info("Configuring SqsClient (Emulator mode: {}, Endpoint: {})",
                properties.isEmulatorMode(), properties.getEndpoint());

        var builder = SqsClient.builder()
                .region(region())
                .credentialsProvider(credentialsProvider());

        if (properties.isEmulatorMode() && properties.getEndpoint() != null) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Client s3Client() {
        log.info("Configuring S3Client (Emulator mode: {}, Endpoint: {})",
                properties.isEmulatorMode(), properties.getEndpoint());

        var builder = S3Client.builder()
                .region(region())
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true) // Required for local emulator
                        .build());

        if (properties.isEmulatorMode() && properties.getEndpoint() != null) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public SnsClient snsClient() {
        log.info("Configuring SnsClient (Emulator mode: {}, Endpoint: {})",
                properties.isEmulatorMode(), properties.getEndpoint());

        var builder = SnsClient.builder()
                .region(region())
                .credentialsProvider(credentialsProvider());

        if (properties.isEmulatorMode() && properties.getEndpoint() != null) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamoDbClient dynamoDbClient() {
        log.info("Configuring DynamoDbClient (Emulator mode: {}, Endpoint: {})",
                properties.isEmulatorMode(), properties.getEndpoint());

        var builder = DynamoDbClient.builder()
                .region(region())
                .credentialsProvider(credentialsProvider());

        if (properties.isEmulatorMode() && properties.getEndpoint() != null) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }

        return builder.build();
    }
}
