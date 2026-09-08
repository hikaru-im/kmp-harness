package im.hikaru.ruoyi.module.system.controller.admin.user.vo.user

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import jakarta.validation.constraints.NotNull

class UserUpdateStatusReqVO {
    @field:NotNull var id: Long? = null
    @field:NotNull @field:InEnum(CommonStatusEnum::class) var status: Int? = null
}
