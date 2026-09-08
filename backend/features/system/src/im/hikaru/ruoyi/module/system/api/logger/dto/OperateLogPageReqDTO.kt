package im.hikaru.ruoyi.module.system.api.logger.dto

import im.hikaru.ruoyi.framework.common.pojo.PageParam

class OperateLogPageReqDTO : PageParam() {
    var type: String? = null
    var bizId: Long? = null
    var userId: Long? = null
}
