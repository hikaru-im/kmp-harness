package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

@Schema(description = "Admin demo category create/update request")
class Demo02CategorySaveReqVO {
    var id: Long? = null
    @field:NotEmpty(message = "Name must not be empty")
    var name: String? = null
    @field:NotNull(message = "Parent id must not be null")
    var parentId: Long? = null
}
