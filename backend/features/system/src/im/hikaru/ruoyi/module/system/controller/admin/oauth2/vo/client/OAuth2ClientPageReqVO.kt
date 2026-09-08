package im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client

import im.hikaru.ruoyi.framework.common.pojo.PageParam

class OAuth2ClientPageReqVO : PageParam() {
    var name: String? = null
    var status: Int? = null
}
