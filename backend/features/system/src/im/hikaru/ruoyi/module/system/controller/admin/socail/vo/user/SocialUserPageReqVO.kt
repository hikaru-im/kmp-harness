package im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import kotlinx.datetime.LocalDateTime

class SocialUserPageReqVO : PageParam() {
    var type: Int? = null
    var nickname: String? = null
    var openid: String? = null
    var createTime: List<LocalDateTime>? = null
}
