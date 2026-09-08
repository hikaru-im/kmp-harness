package im.hikaru.ruoyi.framework.banner.config

import im.hikaru.ruoyi.framework.banner.core.BannerApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean

@AutoConfiguration
class YudaoBannerAutoConfiguration {

    @Bean
    fun bannerApplicationRunner(): BannerApplicationRunner = BannerApplicationRunner()
}
