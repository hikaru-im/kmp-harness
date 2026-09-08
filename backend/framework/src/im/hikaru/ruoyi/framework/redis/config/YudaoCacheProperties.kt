package im.hikaru.ruoyi.framework.redis.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Cache 配置项
 *
 * @author Wanwan
 */
@ConfigurationProperties("yudao.cache")
@Validated
class YudaoCacheProperties {
    /** redis scan 一次返回数量 */
    var redisScanBatchSize: Int = REDIS_SCAN_BATCH_SIZE_DEFAULT

    companion object {
        private const val REDIS_SCAN_BATCH_SIZE_DEFAULT = 30
    }
}
