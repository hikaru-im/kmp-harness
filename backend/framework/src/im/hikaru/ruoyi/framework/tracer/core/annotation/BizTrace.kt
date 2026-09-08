package im.hikaru.ruoyi.framework.tracer.core.annotation

/**
 * 打印业务编号 / 业务类型注解 (迁移自 Java)
 *
 * 使用时，需要设置 SkyWalking OAP Server 的 application.yaml 配置文件，修改 SW_SEARCHABLE_TAG_KEYS 配置项，
 * 增加 biz.type 和 biz.id 两值，然后重启 SkyWalking OAP Server 服务器。
 *
 * @author 麻薯
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BizTrace(
    /** 操作名 */
    val operationName: String = "",
    /** 业务编号 */
    val id: String,
    /** 业务类型 */
    val type: String,
) {
    companion object {
        /** 业务编号 tag 名 */
        const val ID_TAG = "biz.id"
        /** 业务类型 tag 名 */
        const val TYPE_TAG = "biz.type"
    }
}
