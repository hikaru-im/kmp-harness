package im.hikaru.ruoyi.framework.mq.redis.config

import im.hikaru.ruoyi.framework.common.enums.DocumentEnum
import im.hikaru.ruoyi.framework.mq.redis.core.RedisMQTemplate
import im.hikaru.ruoyi.framework.mq.redis.core.job.RedisPendingMessageResendJob
import im.hikaru.ruoyi.framework.mq.redis.core.job.RedisStreamMessageCleanupJob
import im.hikaru.ruoyi.framework.mq.redis.core.pubsub.AbstractRedisChannelMessageListener
import im.hikaru.ruoyi.framework.mq.redis.core.stream.AbstractRedisStreamMessageListener
import im.hikaru.ruoyi.framework.redis.config.YudaoRedisAutoConfiguration
import org.slf4j.LoggerFactory
import org.redisson.api.RedissonClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisServerCommands
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import org.springframework.scheduling.annotation.EnableScheduling
import java.lang.management.ManagementFactory
import java.net.InetAddress

/**
 * Redis 消息队列 Consumer 配置类 (迁移自 Java, 去 Lombok/Hutool)
 *
 * 迁移说明：
 *  - Hutool SystemUtil.getHostInfo().getAddress() / getCurrentPID() → InetAddress + ManagementFactory
 *  - Hutool MapUtil.getStr → Properties.getProperty
 *  - Hutool StrUtil.subBefore/format → Kotlin stdlib
 *
 * @author 芋道源码
 */
@EnableScheduling
@AutoConfiguration(after = [YudaoRedisAutoConfiguration::class])
class YudaoRedisMQConsumerAutoConfiguration {

    /**
     * 创建 Redis Pub/Sub 广播消费的容器
     */
    @Bean
    @ConditionalOnBean(AbstractRedisChannelMessageListener::class)
    fun redisMessageListenerContainer(
        redisMQTemplate: RedisMQTemplate,
        listeners: List<AbstractRedisChannelMessageListener<*>>,
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(redisMQTemplate.redisTemplate.requiredConnectionFactory)
        listeners.forEach { listener ->
            listener.redisMQTemplate = redisMQTemplate
            container.addMessageListener(listener, ChannelTopic(listener.getChannel()))
            log.info(
                "[redisMessageListenerContainer][注册 Channel({}) 对应的监听器({})]",
                listener.getChannel(), listener.javaClass.name,
            )
        }
        return container
    }

    @Bean
    @ConditionalOnBean(AbstractRedisStreamMessageListener::class)
    fun redisPendingMessageResendJob(
        listeners: List<AbstractRedisStreamMessageListener<*>>,
        redisTemplate: RedisMQTemplate,
        redissonClient: RedissonClient,
    ): RedisPendingMessageResendJob =
        RedisPendingMessageResendJob(listeners, redisTemplate, redissonClient, RedisPendingMessageResendJob.DEFAULT_RESEND_LOCK_KEY)

    @Bean
    @ConditionalOnBean(AbstractRedisStreamMessageListener::class)
    fun redisStreamMessageCleanupJob(
        listeners: List<AbstractRedisStreamMessageListener<*>>,
        redisTemplate: RedisMQTemplate,
        redissonClient: RedissonClient,
    ): RedisStreamMessageCleanupJob =
        RedisStreamMessageCleanupJob(listeners, redisTemplate, redissonClient, RedisStreamMessageCleanupJob.DEFAULT_CLEANUP_LOCK_KEY)

    /**
     * 创建 Redis Stream 集群消费的容器
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnBean(AbstractRedisStreamMessageListener::class)
    fun redisStreamMessageListenerContainer(
        redisMQTemplate: RedisMQTemplate,
        listeners: List<AbstractRedisStreamMessageListener<*>>,
    ): StreamMessageListenerContainer<String, ObjectRecord<String, String>> {
        val redisTemplate = redisMQTemplate.redisTemplate
        checkRedisVersion(redisTemplate)
        // 第一步，创建 StreamMessageListenerContainer 容器
        val containerOptions =
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .batchSize(10)
                .targetType(String::class.java)
                .build()
        val container = StreamMessageListenerContainer.create(
            redisMQTemplate.redisTemplate.requiredConnectionFactory, containerOptions,
        )
        // 第二步，注册监听器
        val consumerName = buildConsumerName()
        val streamOps = redisMQTemplate.opsForStream()
        listeners.parallelStream().forEach { listener ->
            log.info(
                "[redisStreamMessageListenerContainer][开始注册 StreamKey({}) 对应的监听器({})]",
                listener.streamKey, listener.javaClass.name,
            )
            // 创建 listener 对应的消费者分组
            try {
                streamOps.createGroup(listener.streamKey, listener.group)
            } catch (ignore: Exception) {
            }
            // 设置 listener 对应的 redisTemplate
            listener.redisMQTemplate = redisMQTemplate
            // 创建 Consumer 对象
            val consumer = Consumer.from(listener.group, consumerName)
            val streamOffset = StreamOffset.create(listener.streamKey, ReadOffset.lastConsumed())
            val builder = StreamMessageListenerContainer.StreamReadRequest
                .builder(streamOffset).consumer(consumer)
                .autoAcknowledge(false)
                .cancelOnError { false }
            container.register(builder.build(), listener)
            log.info(
                "[redisStreamMessageListenerContainer][完成注册 StreamKey({}) 对应的监听器({})]",
                listener.streamKey, listener.javaClass.name,
            )
        }
        return container
    }

    companion object {
        private val log = LoggerFactory.getLogger(YudaoRedisMQConsumerAutoConfiguration::class.java)

        /**
         * 构建消费者名字，使用本地 IP + 进程编号的方式。
         *
         * 迁移说明：Hutool SystemUtil → InetAddress + ManagementFactory
         */
        @JvmStatic
        fun buildConsumerName(): String {
            val hostAddress = InetAddress.getLocalHost().hostAddress
            // 获取进程号 (替代 Hutool SystemUtil.getCurrentPID)
            val pid = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
            return "$hostAddress@$pid"
        }

        /**
         * 校验 Redis 版本号，是否满足最低的版本号要求！
         */
        @JvmStatic
        fun checkRedisVersion(redisTemplate: RedisTemplate<String, *>) {
            val info = redisTemplate.execute(RedisCallback { connection: RedisServerCommands ->
                connection.info()
            })
            val version = info?.getProperty("redis_version") ?: ""
            val majorVersion = version.substringBefore('.').toInt()
            if (majorVersion < 5) {
                throw IllegalStateException(
                    "您当前的 Redis 版本为 $version，小于最低要求的 5.0.0 版本！" +
                        "请参考 ${DocumentEnum.REDIS_INSTALL.url} 文档进行安装。",
                )
            }
        }
    }
}
