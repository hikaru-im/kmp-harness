package im.hikaru.ruoyi.module.system.controller.admin.user.vo.user

import cn.idev.excel.annotation.ExcelProperty
import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import im.hikaru.ruoyi.framework.excel.core.convert.DictConvert
import im.hikaru.ruoyi.module.system.enums.DictTypeConstants
import io.swagger.v3.oas.annotations.media.Schema

class UserImportExcelVO {
    @ExcelProperty("Username")
    var username: String? = null

    @ExcelProperty("Nickname")
    var nickname: String? = null

    @ExcelProperty("Department id")
    var deptId: Long? = null

    @ExcelProperty("Email")
    var email: String? = null

    @ExcelProperty("Mobile")
    var mobile: String? = null

    @ExcelProperty("Sex", converter = DictConvert::class)
    @field:DictFormat(DictTypeConstants.USER_SEX)
    var sex: Int? = null

    @ExcelProperty("Status", converter = DictConvert::class)
    @field:DictFormat(DictTypeConstants.COMMON_STATUS)
    var status: Int? = null
}

@Schema(description = "Admin - user import result")
class UserImportRespVO(
    @field:Schema(description = "Created usernames", requiredMode = Schema.RequiredMode.REQUIRED)
    val createUsernames: MutableList<String> = mutableListOf(),
    @field:Schema(description = "Updated usernames", requiredMode = Schema.RequiredMode.REQUIRED)
    val updateUsernames: MutableList<String> = mutableListOf(),
    @field:Schema(description = "Failed usernames and reasons", requiredMode = Schema.RequiredMode.REQUIRED)
    val failureUsernames: MutableMap<String, String> = linkedMapOf(),
)
