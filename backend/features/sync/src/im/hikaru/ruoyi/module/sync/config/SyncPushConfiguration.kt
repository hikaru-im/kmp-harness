package im.hikaru.ruoyi.module.sync.config

import com.google.auth.oauth2.GoogleCredentials
import im.hikaru.ruoyi.module.sync.service.FirebaseHttpV1SyncPushGateway
import im.hikaru.ruoyi.module.sync.service.SyncPushGateway
import java.net.http.HttpClient
import java.time.Duration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@ConfigurationProperties("yudao.sync.push.firebase")
class SyncFirebasePushProperties {
    var credentialsLocation: String? = null
    var projectId: String? = null
}

@Configuration
@EnableConfigurationProperties(SyncFirebasePushProperties::class)
@ConditionalOnProperty(prefix = "yudao.sync.push.firebase", name = ["enabled"], havingValue = "true")
class SyncPushConfiguration {
    @Bean
    fun syncPushGateway(
        properties: SyncFirebasePushProperties,
        resourceLoader: ResourceLoader,
    ): SyncPushGateway {
        val credentials = properties.credentialsLocation
            ?.takeIf(String::isNotBlank)
            ?.let { location ->
                resourceLoader.getResource(location).inputStream.use(GoogleCredentials::fromStream)
            }
            ?: GoogleCredentials.getApplicationDefault()
        val projectId = requireNotNull(properties.projectId?.takeIf(String::isNotBlank)) {
            "yudao.sync.push.firebase.project-id is required when Firebase sync push is enabled"
        }
        return FirebaseHttpV1SyncPushGateway(
            credentials = credentials.createScoped(FCM_SCOPE),
            projectId = projectId,
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(),
        )
    }

    @Bean("syncPushTaskExecutor")
    fun syncPushTaskExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 1
        maxPoolSize = 4
        queueCapacity = 1_000
        setThreadNamePrefix("sync-push-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(10)
        initialize()
    }

    private companion object {
        const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
    }
}
