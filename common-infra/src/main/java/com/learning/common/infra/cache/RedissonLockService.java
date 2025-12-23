package com.learning.common.infra.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson-backed implementation of DistributedLockService.
 * Uses Redisson's RLock for distributed locking across service instances.
 * 
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Reentrant locks</li>
 * <li>Automatic lease expiration (prevents deadlocks)</li>
 * <li>Waittime for lock acquisition</li>
 * <li>Thread-safe</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!test")
public class RedissonLockService implements DistributedLockService {

    private static final String LOCK_PREFIX = "lock:";

    private final RedissonClient redissonClient;

    @Override
    public boolean tryLock(String lockName, Duration waitTime, Duration leaseTime) {
        try {
            RLock lock = redissonClient.getLock(LOCK_PREFIX + lockName);
            boolean acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);

            if (acquired) {
                log.debug("Lock acquired: {}", lockName);
            } else {
                log.debug("Lock not acquired (timeout): {}", lockName);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted: {}", lockName);
            return false;
        } catch (Exception e) {
            log.error("Lock acquisition failed: {} - {}", lockName, e.getMessage());
            return false;
        }
    }

    @Override
    public void unlock(String lockName) {
        try {
            RLock lock = redissonClient.getLock(LOCK_PREFIX + lockName);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: {}", lockName);
            } else {
                log.warn("Cannot unlock {} - not held by current thread", lockName);
            }
        } catch (Exception e) {
            log.error("Unlock failed: {} - {}", lockName, e.getMessage());
        }
    }

    @Override
    public boolean isHeldByCurrentThread(String lockName) {
        try {
            RLock lock = redissonClient.getLock(LOCK_PREFIX + lockName);
            return lock.isHeldByCurrentThread();
        } catch (Exception e) {
            log.warn("Lock check failed: {} - {}", lockName, e.getMessage());
            return false;
        }
    }

    @Override
    public <T> T executeWithLock(String lockName, Duration waitTime, Duration leaseTime, Supplier<T> action) {
        if (tryLock(lockName, waitTime, leaseTime)) {
            try {
                return action.get();
            } finally {
                unlock(lockName);
            }
        }
        log.warn("Could not acquire lock for action: {}", lockName);
        return null;
    }

    @Override
    public boolean executeWithLock(String lockName, Duration waitTime, Duration leaseTime, Runnable action) {
        if (tryLock(lockName, waitTime, leaseTime)) {
            try {
                action.run();
                return true;
            } finally {
                unlock(lockName);
            }
        }
        log.warn("Could not acquire lock for action: {}", lockName);
        return false;
    }
}
