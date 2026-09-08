package im.hikaru.ruoyi.module.system.controller.admin.dept.vo.post

import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import im.hikaru.ruoyi.framework.excel.core.convert.DictConvert
import im.hikaru.ruoyi.module.system.enums.DictTypeConstants
import java.time.LocalDateTime

@ExcelIgnoreUnannotated
class PostRespVO {
    @ExcelProperty("Post id") var id: Long? = null
    @ExcelProperty("Post name") var name: String? = null
    @ExcelProperty("Post code") var code: String? = null
    @ExcelProperty("Sort") var sort: Int? = null
    @ExcelProperty("Status", converter = DictConvert::class)
    @field:DictFormat(DictTypeConstants.COMMON_STATUS)
    var status: Int? = null
    var remark: String? = null
    var createTime: LocalDateTime? = null
}
