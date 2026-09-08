package im.hikaru.ruoyi.module.system.controller.admin.user.vo.user

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import kotlinx.datetime.LocalDateTime

class UserPageReqVO : PageParam() {
    var username: String? = null
    var mobile: String? = null
    var status: Int? = null
    var createTime: List<LocalDateTime>? = null
    var deptId: Long? = null
    var roleId: Long? = null
}
