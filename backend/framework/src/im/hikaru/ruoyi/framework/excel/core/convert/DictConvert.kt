package im.hikaru.ruoyi.framework.excel.core.convert

import im.hikaru.ruoyi.framework.dict.core.DictFrameworkUtils
import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import cn.idev.excel.converters.Converter
import cn.idev.excel.enums.CellDataTypeEnum
import cn.idev.excel.metadata.GlobalConfiguration
import cn.idev.excel.metadata.data.ReadCellData
import cn.idev.excel.metadata.data.WriteCellData
import cn.idev.excel.metadata.property.ExcelContentProperty
import org.slf4j.LoggerFactory

/**
 * Excel 数据字典转换器 (迁移自 Java, 去 Hutool Convert)
 *
 * @author 芋道源码
 */
class DictConvert : Converter<Any> {

    override fun supportJavaTypeKey(): Class<*> =
        throw UnsupportedOperationException("暂不支持，也不需要")

    override fun supportExcelTypeKey(): CellDataTypeEnum =
        throw UnsupportedOperationException("暂不支持，也不需要")

    override fun convertToJavaData(
        readCellData: ReadCellData<*>,
        contentProperty: ExcelContentProperty,
        globalConfiguration: GlobalConfiguration?,
    ): Any? {
        // 使用字典解析
        val type = getType(contentProperty)
        val label = readCellData.stringValue
        val value = DictFrameworkUtils.parseDictDataValue(type, label)
        if (value == null) {
            log.error("[convertToJavaData][type({}) 解析不掉 label({})]", type, label)
            return null
        }
        // 将 String 的 value 转换成对应的属性 (Hutool Convert.convert → 简单类型转换)
        val fieldClazz = contentProperty.field.type
        return when (fieldClazz) {
            Integer::class.java, Int::class.java -> value.toIntOrNull()
            java.lang.Long::class.java, Long::class.java -> value.toLongOrNull()
            String::class.java -> value
            else -> value
        }
    }

    override fun convertToExcelData(
        value: Any?,
        contentProperty: ExcelContentProperty,
        globalConfiguration: GlobalConfiguration?,
    ): WriteCellData<String> {
        // 空时，返回空
        if (value == null) {
            return WriteCellData("")
        }

        // 使用字典格式化
        val type = getType(contentProperty)
        val valueStr = value.toString()
        val label = DictFrameworkUtils.parseDictDataLabel(type, valueStr)
        if (label == null) {
            log.error("[convertToExcelData][type({}) 转换不了 label({})]", type, valueStr)
            return WriteCellData("")
        }
        // 生成 Excel 小表格
        return WriteCellData(label)
    }

    private fun getType(contentProperty: ExcelContentProperty): String =
        contentProperty.field.getAnnotation(DictFormat::class.java).value

    companion object {
        private val log = LoggerFactory.getLogger(DictConvert::class.java)
    }
}
