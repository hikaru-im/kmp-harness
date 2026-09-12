package im.hikaru.ruoyi.server

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory

@SpringBootTest(
    classes = [YudaoServerApplication::class, YudaoServerApplicationTest.TestDependencies::class],
    properties = [
        "spring.profiles.active=test",
        "spring.main.lazy-initialization=true",
        "spring.datasource.url=jdbc:h2:mem:yudao_server;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.cache.type=simple",
        "spring.quartz.auto-startup=false",
        "spring.boot.admin.client.enabled=false",
        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
        "yudao.websocket.enable=false",
        "yudao.tenant.enable=false",
        "yudao.api-encrypt.enable=false",
        "yudao.xss.enable=false",
    ],
)
class YudaoServerApplicationTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun productionApplicationStartsWithMemberAuthRegistered() {
        assertTrue(applicationContext.containsBeanDefinition("appAuthController"))
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestDependencies {
        @Bean
        fun redissonClient(): RedissonClient = mock(RedissonClient::class.java)

        @Bean
        fun redisConnectionFactory(): RedisConnectionFactory = mock(RedisConnectionFactory::class.java)
    }
}
