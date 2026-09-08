package im.hikaru.ruoyi.module.system.controller.admin.notice.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam

class NoticePageReqVO : PageParam() {
    var title: String? = null
    var status: Int? = null
}
