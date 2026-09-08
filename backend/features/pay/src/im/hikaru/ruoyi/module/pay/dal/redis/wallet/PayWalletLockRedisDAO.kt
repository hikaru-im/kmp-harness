package im.hikaru.ruoyi.module.pay.dal.redis.wallet

import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.concurrent.TimeUnit

@Repository
class PayWalletLockRedisDAO(
    private val redissonClient: RedissonClient,
) {
    fun <T> withLock(id: Long, leaseTime: Duration, action: () -> T): T {
        val lock = redissonClient.getLock("pay_wallet:lock:$id")
        lock.lock(leaseTime.toMillis(), TimeUnit.MILLISECONDS)
        try {
            return action()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}
