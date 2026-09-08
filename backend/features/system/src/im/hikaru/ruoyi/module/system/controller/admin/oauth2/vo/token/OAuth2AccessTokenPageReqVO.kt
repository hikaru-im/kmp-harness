package im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.token

import im.hikaru.ruoyi.framework.common.pojo.PageParam

class OAuth2AccessTokenPageReqVO : PageParam() {
    var userId: Long? = null
    var userType: Int? = null
    var clientId: String? = null
}
