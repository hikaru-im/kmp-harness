package im.hikaru.ruoyi.module.system.controller.admin.notice.vo

import java.time.LocalDateTime

class NoticeRespVO {
    var id: Long? = null
    var title: String? = null
    var type: Int? = null
    var content: String? = null
    var status: Int? = null
    var createTime: LocalDateTime? = null
}
