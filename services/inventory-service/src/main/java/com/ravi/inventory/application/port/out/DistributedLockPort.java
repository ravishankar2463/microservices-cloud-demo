package com.ravi.inventory.application.port.out;

import java.time.Duration;

public interface DistributedLockPort {

    record LockToken(String key, String token, boolean acquired) {}

    LockToken acquire(String lockKey, Duration timeout);
    void release(LockToken lockToken);
}
