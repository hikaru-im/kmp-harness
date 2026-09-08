package im.hikaru.ruoyi.module.system.controller.admin.logger.vo.loginlog

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import kotlinx.datetime.LocalDateTime

class LoginLogPageReqVO : PageParam() {
    var userIp: String? = null
    var username: String? = null
    var status: Boolean? = null
    var createTime: List<LocalDateTime>? = null
}
