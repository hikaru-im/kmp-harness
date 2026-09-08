package im.hikaru.ruoyi.framework.excel.core.function

/**
 * Excel 列下拉数据源获取接口 (迁移自 Java)
 *
 * 为什么不直接解析字典还搞个接口？考虑到有的下拉数据不是从字典中获取的所有需要做一个兼容
 *
 * @author HUIHUI
 */
interface ExcelColumnSelectFunction {

    /**
     * 获得方法名称
     */
    fun getName(): String

    /**
     * 获得列下拉数据源
     */
    fun getOptions(): List<String>
}
