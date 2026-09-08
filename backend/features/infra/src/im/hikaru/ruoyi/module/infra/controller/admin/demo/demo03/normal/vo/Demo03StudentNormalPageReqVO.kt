package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

@Schema(description = "Admin student page request (normal mode)")
class Demo03StudentNormalPageReqVO : PageParam() {
    var name: String? = null
    var sex: Int? = null
    var description: String? = null

    @field:DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var createTime: Array<LocalDateTime>? = null
}
