package com.learning.common.infra.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * No-op implementation of DistributedLockService for tests.
 * All lock operations succeed immediately without actual locking.
 */
@Slf4j
@Service
@Profile("test")
public class NoOpLockService implements DistributedLockService {

    @Override
    public boolean tryLock(String lockName, Duration waitTime, Duration leaseTime) {
        log.debug("NoOp tryLock: {} (always succeeds)", lockName);
        return true;
    }

    @Override
    public void unlock(String lockName) {
        log.debug("NoOp unlock: {}", lockName);
    }

    @Override
    public boolean isHeldByCurrentThread(String lockName) {
        return false;
    }

    @Override
    public <T> T executeWithLock(String lockName, Duration waitTime, Duration leaseTime, Supplier<T> action) {
        log.debug("NoOp executeWithLock: {}", lockName);
        return action.get();
    }

    @Override
    public boolean executeWithLock(String lockName, Duration waitTime, Duration leaseTime, Runnable action) {
        log.debug("NoOp executeWithLock: {}", lockName);
        action.run();
        return true;
    }
}
