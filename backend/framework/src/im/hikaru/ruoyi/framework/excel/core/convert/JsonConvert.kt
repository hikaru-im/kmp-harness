package im.hikaru.ruoyi.framework.excel.core.convert

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import cn.idev.excel.converters.Converter
import cn.idev.excel.enums.CellDataTypeEnum
import cn.idev.excel.metadata.GlobalConfiguration
import cn.idev.excel.metadata.data.WriteCellData
import cn.idev.excel.metadata.property.ExcelContentProperty

/**
 * Excel Json 转换器 (迁移自 Java)
 *
 * @author 芋道源码
 */
class JsonConvert : Converter<Any> {

    override fun supportJavaTypeKey(): Class<*> =
        throw UnsupportedOperationException("暂不支持，也不需要")

    override fun supportExcelTypeKey(): CellDataTypeEnum =
        throw UnsupportedOperationException("暂不支持，也不需要")

    override fun convertToExcelData(
        value: Any?,
        contentProperty: ExcelContentProperty?,
        globalConfiguration: GlobalConfiguration?,
    ): WriteCellData<String> =
        WriteCellData(JsonUtils.toJsonString(value))
}
