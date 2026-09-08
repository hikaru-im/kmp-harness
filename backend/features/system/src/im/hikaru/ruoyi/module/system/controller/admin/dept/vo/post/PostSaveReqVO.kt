package im.hikaru.ruoyi.module.system.controller.admin.dept.vo.post

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

class PostSaveReqVO {
    var id: Long? = null
    @field:NotBlank @field:Size(max = 50) var name: String? = null
    @field:NotBlank @field:Size(max = 64) var code: String? = null
    @field:NotNull var sort: Int? = null
    @field:InEnum(CommonStatusEnum::class) var status: Int? = null
    var remark: String? = null
}
