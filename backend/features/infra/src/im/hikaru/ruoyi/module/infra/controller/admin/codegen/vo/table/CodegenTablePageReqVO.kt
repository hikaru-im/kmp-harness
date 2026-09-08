package im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import java.time.LocalDateTime

class CodegenTablePageReqVO : PageParam() {
    var tableName: String? = null
    var tableComment: String? = null
    var className: String? = null
    var createTime: Array<LocalDateTime>? = null
}
