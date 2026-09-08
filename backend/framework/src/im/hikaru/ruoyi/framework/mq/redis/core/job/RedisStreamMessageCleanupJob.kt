package im.hikaru.ruoyi.framework.mq.redis.core.job

import im.hikaru.ruoyi.framework.mq.redis.core.RedisMQTemplate
import im.hikaru.ruoyi.framework.mq.redis.core.stream.AbstractRedisStreamMessageListener
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StreamOperations
import org.springframework.scheduling.annotation.Scheduled

/**
 * Redis Stream 消息清理任务 (迁移自 Java, 去 Lombok)
 *
 * 用于定期清理已消费的消息，防止内存占用过大
 *
 * @author 芋道源码
 */
class RedisStreamMessageCleanupJob(
    private val listeners: List<AbstractRedisStreamMessageListener<*>>,
    private val redisTemplate: RedisMQTemplate,
    private val redissonClient: RedissonClient,
    private val cleanupLockKey: String,
) {
    @Scheduled(cron = "0 0 * * * ?")
    fun cleanup() {
        val lock: RLock = redissonClient.getLock(cleanupLockKey)
        if (lock.tryLock()) {
            try {
                execute()
            } catch (ex: Exception) {
                log.error("[cleanup][执行异常][lockKey={}]", cleanupLockKey, ex)
            } finally {
                if (lock.isHeldByCurrentThread) {
                    lock.unlock()
                }
            }
        } else {
            log.debug("[cleanup][未获取到锁，跳过本轮][lockKey={}]", cleanupLockKey)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun execute() {
        val ops: StreamOperations<String, Any, Any> = redisTemplate.opsForStream()
        listeners.forEach { listener ->
            try {
                // 使用 XTRIM MAXLEN 精确裁剪（approximate=false）
                val trimCount = ops.trim(listener.streamKey, MAX_COUNT, false)
                if (trimCount != null && trimCount > 0) {
                    log.info("[execute][Stream({}) 清理消息数量({})]", listener.streamKey, trimCount)
                }
            } catch (ex: Exception) {
                log.error("[execute][Stream({}) 清理异常]", listener.streamKey, ex)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RedisStreamMessageCleanupJob::class.java)

        const val DEFAULT_CLEANUP_LOCK_KEY = "redis:stream:message-cleanup:lock"
        const val IOT_CLEANUP_LOCK_KEY = "redis:stream:message-cleanup:lock:iot"

        /** 保留的消息数量，默认保留最近 10000 条消息 */
        private const val MAX_COUNT = 10000L
    }
}
