package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Admin demo category list request")
class Demo02CategoryListReqVO {
    var name: String? = null
    var parentId: Long? = null
    var createTime: Array<LocalDateTime>? = null
}
