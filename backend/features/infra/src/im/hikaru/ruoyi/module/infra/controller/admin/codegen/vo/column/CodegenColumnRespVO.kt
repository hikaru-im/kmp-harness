package im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.column

import java.time.LocalDateTime

class CodegenColumnRespVO : CodegenColumnSaveReqVO() {
    var createTime: LocalDateTime? = null
}
