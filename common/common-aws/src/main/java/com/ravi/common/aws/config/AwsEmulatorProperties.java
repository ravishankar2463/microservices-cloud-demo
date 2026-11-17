package com.ravi.common.aws.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for AWS SDK integration and Local AWS Emulator.
 */
@ConfigurationProperties(prefix = "aws")
public class AwsEmulatorProperties {

    /**
     * Endpoint URL for local AWS emulation (e.g., http://localhost:4566 for LocalStack / local emulator).
     */
    private String endpoint = "http://localhost:4566";

    /**
     * AWS Region (default: us-east-1).
     */
    private String region = "us-east-1";

    /**
     * Mock AWS Access Key for local testing without signing in.
     */
    private String accessKey = "test";

    /**
     * Mock AWS Secret Key for local testing without signing in.
     */
    private String secretKey = "test";

    /**
     * Flag indicating if emulator mode (local no-sign-in) is active.
     */
    private boolean emulatorMode = true;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isEmulatorMode() {
        return emulatorMode;
    }

    public void setEmulatorMode(boolean emulatorMode) {
        this.emulatorMode = emulatorMode;
    }
}
