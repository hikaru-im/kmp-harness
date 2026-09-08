package im.hikaru.ruoyi.framework.tenant.core.aop

/**
 * 忽略租户，标记指定方法不进行租户的自动过滤 (迁移自 Java)
 *
 * @author 芋道源码
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class TenantIgnore(
    /** 是否开启忽略租户，默认 true；支持 Spring EL 表达式 */
    val enable: String = "true",
)
