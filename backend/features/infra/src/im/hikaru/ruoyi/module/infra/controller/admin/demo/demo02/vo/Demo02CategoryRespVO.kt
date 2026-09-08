package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Admin demo category response")
class Demo02CategoryRespVO {
    var id: Long? = null
    var name: String? = null
    var parentId: Long? = null
    var createTime: LocalDateTime? = null
}
