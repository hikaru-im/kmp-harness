package im.hikaru.ruoyi.module.pay.dal.redis.notify

import java.time.Duration
import java.util.concurrent.TimeUnit
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository

@Repository
class PayNotifyLockRedisDAO(
    private val redissonClient: RedissonClient,
) {
    fun withLock(id: Long, leaseTime: Duration, action: () -> Unit) {
        val lock = redissonClient.getLock("pay_notify:lock:$id")
        lock.lock(leaseTime.toMillis(), TimeUnit.MILLISECONDS)
        try {
            action()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}
