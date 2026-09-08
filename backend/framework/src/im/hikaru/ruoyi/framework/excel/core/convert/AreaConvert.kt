package im.hikaru.ruoyi.framework.excel.core.convert

import im.hikaru.ruoyi.framework.ip.core.Area
import im.hikaru.ruoyi.framework.ip.core.utils.AreaUtils
import cn.idev.excel.converters.Converter
import cn.idev.excel.enums.CellDataTypeEnum
import cn.idev.excel.metadata.GlobalConfiguration
import cn.idev.excel.metadata.data.ReadCellData
import cn.idev.excel.metadata.property.ExcelContentProperty
import org.slf4j.LoggerFactory

/**
 * Excel 数据地区转换器 (迁移自 Java, 去 Hutool Convert)
 *
 * @author HUIHUI
 */
class AreaConvert : Converter<Any> {

    override fun supportJavaTypeKey(): Class<*> =
        throw UnsupportedOperationException("暂不支持，也不需要")

    override fun supportExcelTypeKey(): CellDataTypeEnum =
        throw UnsupportedOperationException("暂不支持，也不需要")

    override fun convertToJavaData(
        readCellData: ReadCellData<*>,
        contentProperty: ExcelContentProperty,
        globalConfiguration: GlobalConfiguration?,
    ): Any? {
        // 解析地区编号
        val label = readCellData.stringValue
        val area: Area? = AreaUtils.parseArea(label)
        if (area == null) {
            log.error("[convertToJavaData][label({}) 解析不掉]", label)
            return null
        }
        // 将 value 转换成对应的属性 (Hutool Convert.convert → 简单的类型转换)
        val fieldClazz = contentProperty.field.type
        val areaId = area.id
        return when (fieldClazz) {
            Integer::class.java, Int::class.java -> areaId
            java.lang.Long::class.java, Long::class.java -> areaId?.toLong()
            String::class.java -> areaId?.toString()
            else -> areaId
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AreaConvert::class.java)
    }
}
