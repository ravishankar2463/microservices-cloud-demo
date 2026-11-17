package com.ravi.gateway.infrastructure.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token Bucket Rate Limiter demonstrating DDoS prevention and quota control at the API Gateway.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final int MAX_REQUESTS_PER_WINDOW = 100;

    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    public boolean allowRequest(String clientIp) {
        AtomicInteger counter = requestCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();

        if (currentCount > MAX_REQUESTS_PER_WINDOW) {
            log.warn("Rate limit EXCEEDED for client IP: {} (Count: {})", clientIp, currentCount);
            return false;
        }

        return true;
    }

    public boolean tryAcquire(String clientIp) {
        return allowRequest(clientIp);
    }

    public void reset(String clientIp) {
        requestCounts.remove(clientIp);
    }
}
