package im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role

import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import im.hikaru.ruoyi.framework.excel.core.convert.DictConvert
import im.hikaru.ruoyi.module.system.enums.DictTypeConstants
import java.time.LocalDateTime

@ExcelIgnoreUnannotated
class RoleRespVO {
    @ExcelProperty("Role id") var id: Long? = null
    @ExcelProperty("Role name") var name: String? = null
    @ExcelProperty("Role code") var code: String? = null
    @ExcelProperty("Sort") var sort: Int? = null
    @ExcelProperty("Status", converter = DictConvert::class)
    @field:DictFormat(DictTypeConstants.COMMON_STATUS)
    var status: Int? = null
    var type: Int? = null
    var remark: String? = null
    @ExcelProperty("Data scope", converter = DictConvert::class)
    @field:DictFormat(DictTypeConstants.DATA_SCOPE)
    var dataScope: Int? = null
    var dataScopeDeptIds: Set<Long>? = null
    var createTime: LocalDateTime? = null
}
