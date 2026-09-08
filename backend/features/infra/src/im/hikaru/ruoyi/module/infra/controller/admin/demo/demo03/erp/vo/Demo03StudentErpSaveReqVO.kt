package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.erp.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Schema(description = "Admin student create/update request (ERP mode)")
class Demo03StudentErpSaveReqVO {
    var id: Long? = null

    @field:NotEmpty(message = "Name must not be empty")
    var name: String? = null

    @field:NotNull(message = "Sex must not be null")
    var sex: Int? = null

    @field:NotNull(message = "Birthday must not be null")
    var birthday: LocalDateTime? = null

    @field:NotEmpty(message = "Description must not be empty")
    var description: String? = null
}
