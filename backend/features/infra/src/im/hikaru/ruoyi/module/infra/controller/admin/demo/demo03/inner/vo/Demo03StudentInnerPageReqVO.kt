package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.inner.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

@Schema(description = "Admin student page request (inner mode)")
class Demo03StudentInnerPageReqVO : PageParam() {
    var name: String? = null
    var sex: Int? = null
    var description: String? = null

    @field:DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var createTime: Array<LocalDateTime>? = null
}
