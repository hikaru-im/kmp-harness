package im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table

import java.time.LocalDateTime

class CodegenTableRespVO : CodegenTableSaveReqVO() {
    var dataSourceConfigId: Long? = null
    var createTime: LocalDateTime? = null
    var updateTime: LocalDateTime? = null
}
