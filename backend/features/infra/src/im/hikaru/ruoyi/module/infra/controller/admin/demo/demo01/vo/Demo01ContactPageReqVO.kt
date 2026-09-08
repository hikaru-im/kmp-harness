package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Admin demo contact page request")
class Demo01ContactPageReqVO : PageParam() {
    var name: String? = null
    var sex: Int? = null
    var createTime: Array<LocalDateTime>? = null
}
