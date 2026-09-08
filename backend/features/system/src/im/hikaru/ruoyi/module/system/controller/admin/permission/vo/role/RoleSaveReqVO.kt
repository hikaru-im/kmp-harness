package im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

class RoleSaveReqVO {
    var id: Long? = null; @field:NotBlank @field:Size(max = 30) var name: String? = null; @field:NotBlank @field:Size(max = 100) var code: String? = null
    @field:NotNull var sort: Int? = null; @field:NotNull var status: Int? = null; var remark: String? = null
}
