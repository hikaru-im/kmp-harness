package im.hikaru.ruoyi.module.member.api.level.dto

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum

class MemberLevelRespDTO {
    var id: Long? = null
    var name: String? = null
    var level: Int? = null
    var experience: Int? = null
    var discountPercent: Int? = null
    var status: Int? = null
}
