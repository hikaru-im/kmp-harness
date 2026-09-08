package im.hikaru.ruoyi.framework.excel.core.convert

import cn.idev.excel.converters.Converter
import cn.idev.excel.enums.CellDataTypeEnum
import cn.idev.excel.metadata.GlobalConfiguration
import cn.idev.excel.metadata.data.WriteCellData
import cn.idev.excel.metadata.property.ExcelContentProperty
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 金额转换器 (迁移自 Java)
 *
 * 金额单位：分
 *
 * @author 芋道源码
 */
class MoneyConvert : Converter<Int> {

    override fun supportJavaTypeKey(): Class<*> =
        throw UnsupportedOperationException("暂不支持，也不需要")

    override fun supportExcelTypeKey(): CellDataTypeEnum =
        throw UnsupportedOperationException("暂不支持，也不需要")

    override fun convertToExcelData(
        value: Int?,
        contentProperty: ExcelContentProperty?,
        globalConfiguration: GlobalConfiguration?,
    ): WriteCellData<String> {
        val result = BigDecimal.valueOf(value!!.toLong())
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        return WriteCellData(result.toString())
    }
}
