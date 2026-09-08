package im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo

import jakarta.validation.constraints.NotNull

class CodegenCreateListReqVO {
    @field:NotNull
    var dataSourceConfigId: Long? = null
    @field:NotNull
    var tableNames: List<String>? = null
}
