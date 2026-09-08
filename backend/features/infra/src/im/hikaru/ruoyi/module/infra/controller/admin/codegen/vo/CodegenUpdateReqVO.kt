package im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo

import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.column.CodegenColumnSaveReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.CodegenTableSaveReqVO
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

class CodegenUpdateReqVO {
    @field:Valid
    @field:NotNull
    var table: CodegenTableSaveReqVO? = null
    @field:Valid
    @field:NotNull
    var columns: List<CodegenColumnSaveReqVO>? = null
}
