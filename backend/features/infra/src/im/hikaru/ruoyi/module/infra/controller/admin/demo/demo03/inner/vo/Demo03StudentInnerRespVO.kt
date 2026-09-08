package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.inner.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Admin student response (inner mode)")
class Demo03StudentInnerRespVO {
    var id: Long? = null
    var name: String? = null
    var sex: Int? = null
    var birthday: LocalDateTime? = null
    var description: String? = null
    var createTime: LocalDateTime? = null
}
