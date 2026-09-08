package im.hikaru.ruoyi.module.system.controller.admin.dept.vo.dept

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

class DeptSaveReqVO {
    var id: Long? = null
    @field:NotBlank @field:Size(max = 30) var name: String? = null
    var parentId: Long? = null
    @field:NotNull var sort: Int? = null
    var leaderUserId: Long? = null
    @field:Size(max = 11) var phone: String? = null
    @field:Email @field:Size(max = 50) var email: String? = null
    @field:NotNull @field:InEnum(CommonStatusEnum::class) var status: Int? = null
}
