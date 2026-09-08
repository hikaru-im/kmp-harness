package im.hikaru.ruoyi.framework.excel.core.handler

import im.hikaru.ruoyi.framework.common.core.KeyValue
import im.hikaru.ruoyi.framework.dict.core.DictFrameworkUtils
import im.hikaru.ruoyi.framework.excel.core.annotations.ExcelColumnSelect
import im.hikaru.ruoyi.framework.excel.core.function.ExcelColumnSelectFunction
import cn.idev.excel.annotation.ExcelIgnore
import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import cn.idev.excel.write.handler.SheetWriteHandler
import cn.idev.excel.write.metadata.holder.WriteSheetHolder
import cn.idev.excel.write.metadata.holder.WriteWorkbookHolder
import org.apache.poi.hssf.usermodel.HSSFDataValidation
import org.apache.poi.ss.usermodel.DataValidation
import org.apache.poi.ss.usermodel.DataValidationConstraint
import org.apache.poi.ss.usermodel.DataValidationHelper
import org.apache.poi.ss.usermodel.Name
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.CellRangeAddressList
import org.slf4j.LoggerFactory
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Comparator

/**
 * 基于固定 sheet 实现下拉框 (迁移自 Java, 去 Hutool)
 *
 * 迁移说明：
 *  - Hutool CollUtil.findOne/isEmpty → Kotlin find/isNullOrEmpty
 *  - Hutool StrUtil.isNotEmpty → Kotlin isNotEmpty
 *  - Hutool ObjectUtil.isNotEmpty → Kotlin
 *  - Hutool SpringUtil.getApplicationContext → ApplicationContextAware 注入
 *  - Hutool poi ExcelUtil.indexToColName → 自实现 indexToColName
 *
 * @author HUIHUI
 */
class SelectSheetWriteHandler(
    private val head: Class<*>,
) : SheetWriteHandler, ApplicationContextAware {

    private val selectMap: MutableMap<Int, List<String>> = HashMap()

    init {
        // 解析下拉数据
        var colIndex = 0
        val ignoreUnannotated = head.isAnnotationPresent(ExcelIgnoreUnannotated::class.java)
        for (field in head.declaredFields) {
            // 1.1 忽略 static final 或 transient 的字段
            if (isStaticFinalOrTransient(field)) {
                continue
            }
            // 1.2 忽略的字段跳过
            if ((ignoreUnannotated && !field.isAnnotationPresent(ExcelProperty::class.java)) ||
                field.isAnnotationPresent(ExcelIgnore::class.java)
            ) {
                continue
            }
            // 2. 核心：处理有 ExcelColumnSelect 注解的字段
            if (field.isAnnotationPresent(ExcelColumnSelect::class.java)) {
                val excelProperty = field.getAnnotation(ExcelProperty::class.java)
                if (excelProperty != null && excelProperty.index != -1) {
                    colIndex = excelProperty.index
                }
                getSelectDataList(colIndex, field)
            }
            colIndex++
        }
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    private lateinit var applicationContext: ApplicationContext

    private fun isStaticFinalOrTransient(field: Field): Boolean =
        (Modifier.isStatic(field.modifiers) && Modifier.isFinal(field.modifiers)) ||
            Modifier.isTransient(field.modifiers)

    private fun getSelectDataList(colIndex: Int, field: Field) {
        val columnSelect = field.getAnnotation(ExcelColumnSelect::class.java)
        val dictType = columnSelect.dictType
        val functionName = columnSelect.functionName
        require(dictType.isNotEmpty() || functionName.isNotEmpty()) {
            "Field(${field.name}) 的 @ExcelColumnSelect 注解，dictType 和 functionName 不能同时为空"
        }
        // 情况一：使用 dictType 获得下拉数据
        if (dictType.isNotEmpty()) {
            selectMap[colIndex] = DictFrameworkUtils.getDictDataLabelList(dictType)
            return
        }
        // 情况二：使用 functionName 获得下拉数据
        val functionMap: Map<String, ExcelColumnSelectFunction> =
            applicationContext.getBeansOfType(ExcelColumnSelectFunction::class.java)
        val function = functionMap.values.find { it.getName() == functionName }
        requireNotNull(function) { "未找到对应的 function($functionName)" }
        selectMap[colIndex] = function.getOptions()
    }

    override fun afterSheetCreate(
        writeWorkbookHolder: WriteWorkbookHolder,
        writeSheetHolder: WriteSheetHolder,
    ) {
        if (selectMap.isEmpty()) {
            return
        }
        // 1. 获取相应操作对象
        val helper: DataValidationHelper = writeSheetHolder.sheet.dataValidationHelper
        val workbook: Workbook = writeWorkbookHolder.workbook
        val keyValues: MutableList<KeyValue<Int, List<String>>> =
            selectMap.entries.map { KeyValue(it.key, it.value) }.toMutableList()
        // 升序，否则创建下拉会报错
        keyValues.sortBy { kv: KeyValue<Int, List<String>> -> kv.value?.size ?: 0 }

        // 2. 创建数据字典的 sheet 页
        val dictSheet: Sheet = workbook.createSheet(DICT_SHEET_NAME)
        for (keyValue in keyValues) {
            val colIndex = keyValue.key ?: continue
            val valueList = keyValue.value ?: continue
            val rowLength = valueList.size
            // 2.1 设置字典 sheet 页的值
            for (i in 0 until rowLength) {
                var row: Row? = dictSheet.getRow(i)
                if (row == null) {
                    row = dictSheet.createRow(i)
                }
                row.createCell(colIndex).setCellValue(valueList[i])
            }
            // 2.2 设置单元格下拉选择
            setColumnSelect(writeSheetHolder, workbook, helper, colIndex, valueList)
        }
    }

    private fun setColumnSelect(
        writeSheetHolder: WriteSheetHolder,
        workbook: Workbook,
        helper: DataValidationHelper,
        colIndex: Int,
        valueList: List<String>,
    ) {
        // 1.1 创建可被其他单元格引用的名称
        val name: Name = workbook.createName()
        val excelColumn = indexToColName(colIndex)
        // 1.2 下拉框数据来源 eg:字典sheet!$B1:$B2
        val refers = "$DICT_SHEET_NAME!\$$excelColumn\$1:\$$excelColumn\$${valueList.size}"
        name.nameName = "dict$colIndex"
        name.refersToFormula = refers

        // 2.1 设置约束
        val constraint: DataValidationConstraint = helper.createFormulaListConstraint("dict$colIndex")
        val rangeAddressList = CellRangeAddressList(FIRST_ROW, LAST_ROW, colIndex, colIndex)
        val validation = helper.createValidation(constraint, rangeAddressList)
        if (validation is HSSFDataValidation) {
            validation.suppressDropDownArrow = false
        } else {
            validation.suppressDropDownArrow = true
            validation.showErrorBox = true
        }
        // 2.2 阻止输入非下拉框的值
        validation.errorStyle = DataValidation.ErrorStyle.STOP
        validation.createErrorBox("提示", "此值不存在于下拉选择中！")
        // 2.3 添加下拉框约束
        writeSheetHolder.sheet.addValidationData(validation)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SelectSheetWriteHandler::class.java)

        /** 数据起始行从 0 开始 */
        const val FIRST_ROW = 1
        /** 下拉列需要创建下拉框的行数，默认两千行 */
        const val LAST_ROW = 2000

        private const val DICT_SHEET_NAME = "字典sheet"

        /**
         * 将列索引转换为 Excel 列名 (0→A, 1→B, 26→AA)
         *
         * 替代 Hutool ExcelUtil.indexToColName
         */
        private fun indexToColName(index: Int): String {
            val sb = StringBuilder()
            var col = index
            while (col >= 0) {
                sb.insert(0, ('A'.code + col % 26).toChar())
                col = col / 26 - 1
            }
            return sb.toString()
        }
    }
}
