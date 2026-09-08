package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Schema(description = "Admin demo contact create/update request")
class Demo01ContactSaveReqVO {
    var id: Long? = null
    @field:NotEmpty(message = "Name must not be empty")
    var name: String? = null
    @field:NotNull(message = "Sex must not be null")
    var sex: Int? = null
    @field:NotNull(message = "Birthday must not be null")
    var birthday: LocalDateTime? = null
    @field:NotEmpty(message = "Description must not be empty")
    var description: String? = null
    var avatar: String? = null
}
