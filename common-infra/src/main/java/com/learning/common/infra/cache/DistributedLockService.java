package com.learning.common.infra.cache;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Interface for distributed locking operations.
 * Allows swapping implementations (Redisson, Zookeeper, etc.) without changing
 * callers.
 * 
 * <p>
 * Use cases:
 * </p>
 * <ul>
 * <li>Preventing concurrent updates to same resource</li>
 * <li>Coordinating batch jobs across pods</li>
 * <li>Ensuring exactly-once processing</li>
 * </ul>
 */
public interface DistributedLockService {

    /**
     * Acquire a distributed lock.
     *
     * @param lockName  unique lock identifier
     * @param waitTime  max time to wait for lock acquisition
     * @param leaseTime auto-release lock after this duration
     * @return true if lock acquired, false if timed out
     */
    boolean tryLock(String lockName, Duration waitTime, Duration leaseTime);

    /**
     * Release a previously acquired lock.
     *
     * @param lockName lock identifier to release
     */
    void unlock(String lockName);

    /**
     * Check if current thread holds the lock.
     *
     * @param lockName lock identifier
     * @return true if held by current thread
     */
    boolean isHeldByCurrentThread(String lockName);

    /**
     * Execute action while holding lock.
     * Lock is automatically released after action completes.
     *
     * @param lockName  unique lock identifier
     * @param waitTime  max time to wait for lock
     * @param leaseTime auto-release lock after this duration
     * @param action    action to execute while holding lock
     * @param <T>       return type of action
     * @return result of action, or empty if lock not acquired
     */
    <T> T executeWithLock(String lockName, Duration waitTime, Duration leaseTime, Supplier<T> action);

    /**
     * Execute action while holding lock (fire-and-forget pattern).
     *
     * @param lockName  unique lock identifier
     * @param waitTime  max time to wait for lock
     * @param leaseTime auto-release lock after this duration
     * @param action    action to execute while holding lock
     * @return true if action executed, false if lock not acquired
     */
    boolean executeWithLock(String lockName, Duration waitTime, Duration leaseTime, Runnable action);
}
