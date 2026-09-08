package im.hikaru.ruoyi.framework.tenant.config

import im.hikaru.ruoyi.framework.common.biz.system.tenant.TenantCommonApi
import im.hikaru.ruoyi.framework.common.enums.WebFilterOrderEnum
import im.hikaru.ruoyi.framework.redis.config.YudaoCacheProperties
import im.hikaru.ruoyi.framework.security.core.service.SecurityFrameworkService
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnoreAspect
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.db.TenantDatabaseInterceptor
import im.hikaru.ruoyi.framework.tenant.core.job.TenantJobAspect
import im.hikaru.ruoyi.framework.tenant.core.mq.rabbitmq.TenantRabbitMQInitializer
import im.hikaru.ruoyi.framework.tenant.core.mq.redis.TenantRedisMessageInterceptor
import im.hikaru.ruoyi.framework.tenant.core.mq.rocketmq.TenantRocketMQInitializer
import im.hikaru.ruoyi.framework.tenant.core.mq.kafka.TenantKafkaInitializer
import im.hikaru.ruoyi.framework.tenant.core.redis.TenantRedisCacheManager
import im.hikaru.ruoyi.framework.tenant.core.security.TenantSecurityWebFilter
import im.hikaru.ruoyi.framework.tenant.core.service.TenantFrameworkService
import im.hikaru.ruoyi.framework.tenant.core.service.TenantFrameworkServiceImpl
import im.hikaru.ruoyi.framework.tenant.core.web.TenantContextWebFilter
import im.hikaru.ruoyi.framework.tenant.core.web.TenantVisitContextInterceptor
import im.hikaru.ruoyi.framework.web.config.WebProperties
import im.hikaru.ruoyi.framework.web.core.handler.GlobalExceptionHandler
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.BatchStrategies
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheWriter
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * 多租户自动配置类 (迁移自 Java, 去 Lombok)
 *
 * 迁移说明：
 *  - 移除 MyBatis-Plus 的 TenantLineInnerInterceptor bean (已迁移到 Exposed, 见 TenantDatabaseInterceptor)
 *  - 移除 RocketMQ/Kafka 相关配置 (可选 MQ 集成, 待业务模块启用时迁移)
 *
 * @author 芋道源码
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "yudao.tenant", value = ["enable"], matchIfMissing = true)
@EnableConfigurationProperties(TenantProperties::class)
class YudaoTenantAutoConfiguration(
    private val applicationContext: ApplicationContext,
) {

    @Bean(destroyMethod = "close")
    fun tenantDatabaseInterceptor(tenantProperties: TenantProperties): TenantDatabaseInterceptor =
        TenantDatabaseInterceptor(tenantProperties)

    @Bean
    fun tenantFrameworkService(tenantApi: TenantCommonApi): TenantFrameworkService =
        TenantFrameworkServiceImpl(tenantApi)

    // ========== AOP ==========
    @Bean
    fun tenantIgnoreAspect(): TenantIgnoreAspect = TenantIgnoreAspect()

    // ========== WEB ==========
    @Bean
    fun tenantContextWebFilter(): FilterRegistrationBean<TenantContextWebFilter> {
        val bean = FilterRegistrationBean<TenantContextWebFilter>()
        bean.setFilter(TenantContextWebFilter())
        bean.setOrder(WebFilterOrderEnum.TENANT_CONTEXT_FILTER)
        return bean
    }

    @Bean
    fun tenantVisitContextInterceptor(
        tenantProperties: TenantProperties,
        securityFrameworkService: SecurityFrameworkService,
    ): TenantVisitContextInterceptor = TenantVisitContextInterceptor(tenantProperties, securityFrameworkService)

    @Bean
    fun tenantWebMvcConfigurer(
        tenantProperties: TenantProperties,
        tenantVisitContextInterceptor: TenantVisitContextInterceptor,
    ): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(tenantVisitContextInterceptor)
                .excludePathPatterns(tenantProperties.ignoreVisitUrls.toList())
        }
    }

    // ========== Security ==========
    @Bean
    fun tenantSecurityWebFilter(
        tenantProperties: TenantProperties,
        webProperties: WebProperties,
        globalExceptionHandler: GlobalExceptionHandler,
        tenantFrameworkService: TenantFrameworkService,
    ): FilterRegistrationBean<TenantSecurityWebFilter> {
        val bean = FilterRegistrationBean<TenantSecurityWebFilter>()
        bean.setFilter(
            TenantSecurityWebFilter(
                webProperties,
                tenantProperties,
                getTenantIgnoreUrls(),
                globalExceptionHandler,
                tenantFrameworkService,
            ),
        )
        bean.setOrder(WebFilterOrderEnum.TENANT_SECURITY_FILTER)
        return bean
    }

    /**
     * 如果 Controller 接口上有 [TenantIgnore] 注解，则添加到忽略租户的 URL 集合中。
     */
    @Suppress("removal")
    private fun getTenantIgnoreUrls(): Set<String> {
        val requestMappingHandlerMapping = applicationContext.getBean(
            "requestMappingHandlerMapping",
            RequestMappingHandlerMapping::class.java,
        )
        val ignoreUrls = mutableSetOf<String>()
        requestMappingHandlerMapping.handlerMethods.forEach { (mappingInfo, handlerMethod: HandlerMethod) ->
            if (!handlerMethod.hasMethodAnnotation(TenantIgnore::class.java) &&
                !handlerMethod.beanType.isAnnotationPresent(TenantIgnore::class.java)
            ) {
                return@forEach
            }
            mappingInfo.patternsCondition?.patterns?.let(ignoreUrls::addAll)
            mappingInfo.pathPatternsCondition?.patterns
                ?.mapTo(ignoreUrls) { it.patternString }
        }
        return ignoreUrls
    }

    // ========== MQ ==========
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(YudaoTenantAutoConfiguration::class)
    @ConditionalOnClass(name = ["im.hikaru.ruoyi.framework.mq.redis.core.interceptor.RedisMessageInterceptor"])
    class TenantRedisMQConfiguration {
        @Bean
        fun tenantRedisMessageInterceptor(): TenantRedisMessageInterceptor = TenantRedisMessageInterceptor()
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(YudaoTenantAutoConfiguration::class)
    @ConditionalOnClass(name = ["org.springframework.amqp.rabbit.core.RabbitTemplate"])
    class TenantRabbitMQConfiguration {
        @Bean
        fun tenantRabbitMQInitializer(): TenantRabbitMQInitializer = TenantRabbitMQInitializer()
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(YudaoTenantAutoConfiguration::class)
    @ConditionalOnClass(name = ["org.springframework.kafka.config.AbstractKafkaListenerContainerFactory"])
    class TenantKafkaMQConfiguration {
        @Bean
        fun tenantKafkaInitializer(): TenantKafkaInitializer = TenantKafkaInitializer()
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(YudaoTenantAutoConfiguration::class)
    @ConditionalOnClass(name = ["org.apache.rocketmq.spring.core.RocketMQTemplate"])
    class TenantRocketMQConfiguration {
        @Bean
        fun tenantRocketMQInitializer(): TenantRocketMQInitializer = TenantRocketMQInitializer()
    }

    // ========== Redis ==========
    @Bean
    @Primary
    fun tenantRedisCacheManager(
        redisTemplate: RedisTemplate<String, Any>,
        redisCacheConfiguration: RedisCacheConfiguration,
        yudaoCacheProperties: YudaoCacheProperties,
        tenantProperties: TenantProperties,
    ): TenantRedisCacheManager {
        val connectionFactory: RedisConnectionFactory =
            redisTemplate.connectionFactory ?: error("RedisTemplate 的 ConnectionFactory 不能为空")
        val cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(
            connectionFactory, BatchStrategies.scan(yudaoCacheProperties.redisScanBatchSize),
        )
        val cacheManager = TenantRedisCacheManager(
            im.hikaru.ruoyi.framework.redis.core.TimeoutRedisCacheManager(cacheWriter, redisCacheConfiguration),
            tenantProperties.ignoreCaches,
        )
        return cacheManager
    }

    // ========== Job ==========
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(YudaoTenantAutoConfiguration::class)
    @ConditionalOnClass(name = ["im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler"])
    class TenantJobConfiguration {
        @Bean
        fun tenantJobAspect(tenantFrameworkService: TenantFrameworkService): TenantJobAspect =
            TenantJobAspect(tenantFrameworkService)
    }
}
