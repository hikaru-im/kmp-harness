package im.hikaru.ruoyi.module.system.controller.admin.user.vo.user

import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import im.hikaru.ruoyi.framework.excel.core.convert.DictConvert
import im.hikaru.ruoyi.module.system.enums.DictTypeConstants
import java.time.LocalDateTime

@ExcelIgnoreUnannotated
class UserRespVO {
    @ExcelProperty("User id")
    var id: Long? = null

    @ExcelProperty("Username")
    var username: String? = null

    @ExcelProperty("Nickname")
    var nickname: String? = null

    var remark: String? = null
    var deptId: Long? = null

    @ExcelProperty("Department")
    var deptName: String? = null

    var postIds: Set<Long>? = null

    @ExcelProperty("Email")
    var email: String? = null

    @ExcelProperty("Mobile")
    var mobile: String? = null

    @ExcelProperty("Sex", converter = DictConvert::class)
    @field:DictFormat(DictTypeConstants.USER_SEX)
    var sex: Int? = null

    var avatar: String? = null

    @ExcelProperty("Status", converter = DictConvert::class)
    @field:DictFormat(DictTypeConstants.COMMON_STATUS)
    var status: Int? = null

    @ExcelProperty("Last login IP")
    var loginIp: String? = null

    @ExcelProperty("Last login time")
    var loginDate: LocalDateTime? = null

    var createTime: LocalDateTime? = null
}
