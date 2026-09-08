package im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant

import im.hikaru.ruoyi.framework.common.validation.Mobile
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kotlinx.datetime.LocalDateTime

class TenantSaveReqVO {
    var id: Long? = null; @field:NotBlank @field:Size(max = 30) var name: String? = null; @field:NotBlank var contactName: String? = null; @field:Mobile var contactMobile: String? = null
    @field:NotNull var status: Int? = null; var websites: List<String>? = null; @field:NotNull var packageId: Long? = null; @field:NotNull var expireTime: LocalDateTime? = null; @field:NotNull var accountCount: Int? = null
    var username: String? = null; var password: String? = null
}
