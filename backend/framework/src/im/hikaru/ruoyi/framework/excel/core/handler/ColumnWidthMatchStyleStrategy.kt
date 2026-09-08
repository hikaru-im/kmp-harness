package im.hikaru.ruoyi.framework.excel.core.handler

import cn.idev.excel.enums.CellDataTypeEnum
import cn.idev.excel.metadata.Head
import cn.idev.excel.metadata.data.WriteCellData
import cn.idev.excel.write.metadata.holder.WriteSheetHolder
import cn.idev.excel.write.style.column.AbstractColumnWidthStyleStrategy
import org.apache.poi.ss.usermodel.Cell

/**
 * Excel 自适应列宽处理器 (迁移自 Java, 去 Hutool CollUtil)
 *
 * 相比 LongestMatchColumnWidthStyleStrategy 来说，额外处理了 DATE 类型！
 *
 * @author hmb
 */
class ColumnWidthMatchStyleStrategy : AbstractColumnWidthStyleStrategy() {

    private val cache: MutableMap<Int?, MutableMap<Int, Int>> = HashMap()

    override fun setColumnWidth(
        writeSheetHolder: WriteSheetHolder,
        cellDataList: List<WriteCellData<*>>,
        cell: Cell,
        head: Head,
        relativeRowIndex: Int?,
        isHead: Boolean?,
    ) {
        val needSetWidth = isHead == true || cellDataList.isNotEmpty()
        if (!needSetWidth) {
            return
        }
        val maxColumnWidthMap = cache.computeIfAbsent(writeSheetHolder.sheetNo) { HashMap(16) }
        var columnWidth = dataLength(cellDataList, cell, isHead)
        if (columnWidth < 0) {
            return
        }
        if (columnWidth > MAX_COLUMN_WIDTH) {
            columnWidth = MAX_COLUMN_WIDTH
        }
        val maxColumnWidth = maxColumnWidthMap[cell.columnIndex]
        if (maxColumnWidth == null || columnWidth > maxColumnWidth) {
            maxColumnWidthMap[cell.columnIndex] = columnWidth
            writeSheetHolder.sheet.setColumnWidth(cell.columnIndex, columnWidth * 256)
        }
    }

    private fun dataLength(cellDataList: List<WriteCellData<*>>, cell: Cell, isHead: Boolean?): Int {
        if (isHead == true) {
            return cell.stringCellValue.toByteArray().size
        }
        val cellData = cellDataList[0]
        val type = cellData.type ?: return -1
        return when (type) {
            CellDataTypeEnum.STRING -> cellData.stringValue!!.toByteArray().size
            CellDataTypeEnum.BOOLEAN -> cellData.booleanValue.toString().toByteArray().size
            CellDataTypeEnum.NUMBER -> cellData.numberValue.toString().toByteArray().size
            CellDataTypeEnum.DATE -> cellData.dateValue.toString().toByteArray().size
            else -> -1
        }
    }

    companion object {
        private const val MAX_COLUMN_WIDTH = 255
    }
}
