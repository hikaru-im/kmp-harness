package im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client

import im.hikaru.ruoyi.framework.common.pojo.PageParam

class SocialClientPageReqVO : PageParam() {
    var name: String? = null
    var socialType: Int? = null
    var userType: Int? = null
    var clientId: String? = null
    var status: Int? = null
}
