package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Admin student response (normal mode)")
class Demo03StudentNormalRespVO {
    var id: Long? = null
    var name: String? = null
    var sex: Int? = null
    var birthday: LocalDateTime? = null
    var description: String? = null
    var createTime: LocalDateTime? = null
}
