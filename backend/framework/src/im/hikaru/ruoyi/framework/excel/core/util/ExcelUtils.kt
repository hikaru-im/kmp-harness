package im.hikaru.ruoyi.framework.excel.core.util

import im.hikaru.ruoyi.framework.common.util.http.HttpUtils
import im.hikaru.ruoyi.framework.excel.core.handler.ColumnWidthMatchStyleStrategy
import im.hikaru.ruoyi.framework.excel.core.handler.SelectSheetWriteHandler
import cn.idev.excel.FastExcelFactory
import cn.idev.excel.converters.longconverter.LongStringConverter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.multipart.MultipartFile

/**
 * Excel 工具类 (迁移自 Java, 去 Lombok/Hutool)
 *
 * @author 芋道源码
 */
object ExcelUtils {

    /**
     * 将列表以 Excel 响应给前端
     *
     * @throws java.io.IOException 写入失败的情况
     */
    @Throws(java.io.IOException::class)
    fun <T> write(
        response: HttpServletResponse,
        filename: String,
        sheetName: String,
        head: Class<T>,
        data: List<T>,
    ) {
        // 输出 Excel
        FastExcelFactory.write(response.outputStream, head)
            .autoCloseStream(false) // 不要自动关闭，交给 Servlet 自己处理
            .registerWriteHandler(ColumnWidthMatchStyleStrategy()) // 基于 column 长度，自动适配。最大 255 宽度
            .registerWriteHandler(SelectSheetWriteHandler(head)) // 基于固定 sheet 实现下拉框
            .registerConverter(LongStringConverter()) // 避免 Long 类型丢失精度
            .sheet(sheetName).doWrite(data)
        // 设置 header 和 contentType
        response.addHeader("Content-Disposition", "attachment;filename=" + HttpUtils.encodeUtf8(filename))
        response.contentType = "application/vnd.ms-excel;charset=UTF-8"
    }

    @Throws(java.io.IOException::class)
    fun <T> read(file: MultipartFile, head: Class<T>): List<T> =
        // 参考 https://t.zsxq.com/zM77F 帖子，增加 try 处理，兼容 windows 场景
        file.inputStream.use { inputStream ->
            FastExcelFactory.read(inputStream, head, null)
                .autoCloseStream(false)
                .doReadAllSync()
        }
}
