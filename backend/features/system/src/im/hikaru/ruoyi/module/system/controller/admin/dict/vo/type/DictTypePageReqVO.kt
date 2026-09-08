package im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

class DictTypePageReqVO : PageParam() {
    var name: String? = null
    var type: String? = null
    var status: Int? = null

    @field:DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var createTime: Array<LocalDateTime>? = null
}
