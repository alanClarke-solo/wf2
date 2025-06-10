package ac.wf2.service.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {
    
    private final RedissonClient redissonClient;
    
    public boolean tryLock(String lockKey, long timeout, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(timeout, timeUnit);
            if (acquired) {
                log.debug("Lock acquired: {}", lockKey);
            } else {
                log.warn("Failed to acquire lock: {}", lockKey);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted: {}", lockKey, e);
            return false;
        }
    }
    
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released: {}", lockKey);
        }
    }
    
    public boolean tryAcquireSemaphore(String semaphoreKey, int permits, long timeout, TimeUnit timeUnit) {
        RSemaphore semaphore = redissonClient.getSemaphore(semaphoreKey);
        try {
            boolean acquired = semaphore.tryAcquire(permits, timeout, timeUnit);
            if (acquired) {
                log.debug("Semaphore acquired: {} permits: {}", semaphoreKey, permits);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Semaphore acquisition interrupted: {}", semaphoreKey, e);
            return false;
        }
    }
    
    public void releaseSemaphore(String semaphoreKey, int permits) {
        RSemaphore semaphore = redissonClient.getSemaphore(semaphoreKey);
        semaphore.release(permits);
        log.debug("Semaphore released: {} permits: {}", semaphoreKey, permits);
    }
}