package im.hikaru.ruoyi.framework.excel.core.annotations

/**
 * 字典格式化 (迁移自 Java)
 *
 * 实现将字典数据的值，格式化成字典数据的标签
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class DictFormat(
    /** 字典类型，例如说 SysDictTypeConstants、InfDictTypeConstants */
    val value: String,
)
