package com.ravi.common.aws.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import static org.assertj.core.api.Assertions.assertThat;

class AwsClientConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AwsClientConfiguration.class));

    @Test
    @DisplayName("Should auto-configure AWS SDK v2 clients with emulator defaults")
    void testAwsClientAutoConfiguration() {
        runner.withPropertyValues(
                "aws.endpoint=http://localhost:4566",
                "aws.region=us-east-1",
                "aws.emulator-mode=true"
        ).run(context -> {
            assertThat(context).hasSingleBean(SqsClient.class);
            assertThat(context).hasSingleBean(S3Client.class);
            assertThat(context).hasSingleBean(SnsClient.class);
            assertThat(context).hasSingleBean(DynamoDbClient.class);
        });
    }
}
