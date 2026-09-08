package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Admin demo contact response")
class Demo01ContactRespVO {
    var id: Long? = null
    var name: String? = null
    var sex: Int? = null
    var birthday: LocalDateTime? = null
    var description: String? = null
    var avatar: String? = null
    var createTime: LocalDateTime? = null
}
