package im.hikaru.ruoyi.module.infra.enums.config

/**
 * 参数配置类型枚举 (迁移自 Java)
 *
 * @author 芋道源码
 */
enum class ConfigTypeEnum(val type: Int) {
    /** 系统配置 */
    SYSTEM(1),
    /** 自定义配置 */
    CUSTOM(2),
    ;

    companion object {
        @JvmStatic
        fun getIfPresent(type: Int?): ConfigTypeEnum? =
            type?.let { t -> entries.firstOrNull { it.type == t } }
    }
}
