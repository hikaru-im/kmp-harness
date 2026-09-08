package im.hikaru.ruoyi.framework.excel.core.annotations

/**
 * 给 Excel 列添加下拉选择数据 (迁移自 Java)
 *
 * 其中 [dictType] 和 [functionName] 二选一
 *
 * @author HUIHUI
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExcelColumnSelect(
    /** 字典类型 */
    val dictType: String = "",
    /** 获取下拉数据源的方法名称 */
    val functionName: String = "",
)
