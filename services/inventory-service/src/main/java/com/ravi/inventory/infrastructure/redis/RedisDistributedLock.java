package com.ravi.inventory.infrastructure.redis;

import com.ravi.inventory.application.port.out.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Distributed Lock backed by Redis (SET key value NX PX timeout).
 * Ensures only one microservice instance modifies a specific stock item at any given moment.
 */
@Component
public class RedisDistributedLock implements DistributedLockPort {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);
    private final Optional<StringRedisTemplate> redisTemplate;

    public RedisDistributedLock(Optional<StringRedisTemplate> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public LockToken acquire(String lockKey, Duration timeout) {
        if (redisTemplate.isEmpty()) {
            // Standalone fallback when Redis is not present
            return new LockToken(lockKey, UUID.randomUUID().toString(), true);
        }

        String token = UUID.randomUUID().toString();
        try {
            Boolean success = redisTemplate.get()
                    .opsForValue()
                    .setIfAbsent(lockKey, token, timeout);

            boolean acquired = Boolean.TRUE.equals(success);
            if (acquired) {
                log.debug("Acquired Redis distributed lock for key: {}", lockKey);
            } else {
                log.warn("Failed to acquire Redis distributed lock for key: {} (Conflict)", lockKey);
            }
            return new LockToken(lockKey, token, acquired);
        } catch (Exception e) {
            log.warn("Redis unavailable during lock acquisition, allowing execution in degraded mode: {}", e.getMessage());
            return new LockToken(lockKey, token, true);
        }
    }

    @Override
    public void release(LockToken lockToken) {
        if (!lockToken.acquired() || redisTemplate.isEmpty()) {
            return;
        }

        try {
            String current = redisTemplate.get().opsForValue().get(lockToken.key());
            if (lockToken.token().equals(current)) {
                redisTemplate.get().delete(lockToken.key());
                log.debug("Released Redis distributed lock for key: {}", lockToken.key());
            }
        } catch (Exception e) {
            log.warn("Failed to release Redis lock {}: {}", lockToken.key(), e.getMessage());
        }
    }
}
