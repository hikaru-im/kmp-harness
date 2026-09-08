package im.hikaru.ruoyi.framework.mq.redis.core.job

import im.hikaru.ruoyi.framework.mq.redis.core.RedisMQTemplate
import im.hikaru.ruoyi.framework.mq.redis.core.stream.AbstractRedisStreamMessageListener
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.PendingMessages
import org.springframework.data.redis.connection.stream.PendingMessagesSummary
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StreamOperations
import org.springframework.scheduling.annotation.Scheduled
import java.util.Objects

/**
 * 这个任务用于处理，crash 之后的消费者未消费完的消息 (迁移自 Java, 去 Lombok/Hutool)
 *
 * 迁移说明：Hutool CollUtil.isEmpty → Kotlin isNullOrEmpty
 *
 * @author 芋道源码
 */
class RedisPendingMessageResendJob(
    private val listeners: List<AbstractRedisStreamMessageListener<*>>,
    private val redisTemplate: RedisMQTemplate,
    private val redissonClient: RedissonClient,
    private val resendLockKey: String,
) {
    @Scheduled(cron = "35 * * * * ?")
    fun messageResend() {
        val lock: RLock = redissonClient.getLock(resendLockKey)
        if (lock.tryLock()) {
            try {
                execute()
            } catch (ex: Exception) {
                log.error("[messageResend][执行异常][lockKey={}]", resendLockKey, ex)
            } finally {
                if (lock.isHeldByCurrentThread) {
                    lock.unlock()
                }
            }
        } else {
            log.debug("[messageResend][未获取到锁，跳过本轮][lockKey={}]", resendLockKey)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun execute() {
        val ops: StreamOperations<String, Any, Any> = redisTemplate.opsForStream()
        listeners.forEach { listener ->
            val pendingMessagesSummary: PendingMessagesSummary =
                Objects.requireNonNull(ops.pending(listener.streamKey, listener.group))
            // 每个消费者的 pending 队列消息数量
            val pendingMessagesPerConsumer = pendingMessagesSummary.pendingMessagesPerConsumer
            pendingMessagesPerConsumer.forEach { (consumerName, pendingMessageCount) ->
                log.info("[processPendingMessage][消费者({}) 消息数量({})]", consumerName, pendingMessageCount)
                // 每个消费者的 pending消息的详情信息
                val pendingMessages: PendingMessages = ops.pending(
                    listener.streamKey, Consumer.from(listener.group, consumerName),
                    Range.unbounded<String>(), pendingMessageCount,
                )
                if (pendingMessages.isEmpty) {
                    return@forEach
                }
                pendingMessages.forEach { pendingMessage ->
                    // 获取消息上一次传递到 consumer 的时间
                    val lastDelivery = pendingMessage.elapsedTimeSinceLastDelivery.seconds
                    if (lastDelivery < EXPIRE_TIME) {
                        return@forEach
                    }
                    // 获取指定 id 的消息体
                    val records: List<MapRecord<String, Any, Any>> = ops.range(
                        listener.streamKey,
                        Range.of(Range.Bound.inclusive(pendingMessage.idAsString), Range.Bound.inclusive(pendingMessage.idAsString)),
                    )
                    if (records.isNullOrEmpty()) {
                        return@forEach
                    }
                    // 重新投递消息
                    val resendOps = redisTemplate.opsForStream()
                    resendOps.add(
                        StreamRecords.newRecord()
                            .ofObject(records[0].value)
                            .withStreamKey(listener.streamKey),
                    )
                    // ack 消息消费完成
                    resendOps.acknowledge(listener.group, records[0])
                    log.info("[processPendingMessage][消息({})重新投递成功]", records[0].id)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RedisPendingMessageResendJob::class.java)

        const val DEFAULT_RESEND_LOCK_KEY = "redis:stream:pending-message-resend:lock"
        const val IOT_RESEND_LOCK_KEY = "redis:stream:pending-message-resend:lock:iot"

        /** 消息超时时间，默认 5 分钟 */
        private const val EXPIRE_TIME = 5 * 60L
    }
}
