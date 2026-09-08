package im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo

import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.column.CodegenColumnRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.CodegenTableRespVO

class CodegenDetailRespVO {
    var table: CodegenTableRespVO? = null
    var columns: List<CodegenColumnRespVO>? = null
}
