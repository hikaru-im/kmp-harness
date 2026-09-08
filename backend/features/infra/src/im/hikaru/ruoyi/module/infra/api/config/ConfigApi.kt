package im.hikaru.ruoyi.module.infra.api.config

/**
 * 参数配置 API 接口 (迁移自 Java)
 *
 * @author 芋道源码
 */
interface ConfigApi {

    /**
     * 根据参数键查询参数值
     *
     * @param key 参数键
     * @return 参数值
     */
    fun getConfigValueByKey(key: String): String?
}
