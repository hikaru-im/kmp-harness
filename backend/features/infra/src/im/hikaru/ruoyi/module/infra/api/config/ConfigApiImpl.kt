package im.hikaru.ruoyi.module.infra.api.config

import im.hikaru.ruoyi.module.infra.service.config.ConfigService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

/**
 * 参数配置 API 实现类 (迁移自 Java)
 *
 * @author 芋道源码
 */
@Service
@Validated
class ConfigApiImpl(
    private val configService: ConfigService,
) : ConfigApi {

    override fun getConfigValueByKey(key: String): String? =
        configService.getConfigByKey(key)?.value
}
