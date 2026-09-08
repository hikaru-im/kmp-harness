package im.hikaru.ruoyi.module.infra.framework.file.config

import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientFactory
import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientFactoryImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class YudaoFileAutoConfiguration {
    @Bean
    fun fileClientFactory(): FileClientFactory = FileClientFactoryImpl()
}
