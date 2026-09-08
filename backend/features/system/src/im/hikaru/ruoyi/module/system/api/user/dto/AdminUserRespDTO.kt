package im.hikaru.ruoyi.module.system.api.user.dto

class AdminUserRespDTO {
    var id: Long? = null
    var nickname: String? = null
    var status: Int? = null
    var deptId: Long? = null
    var postIds: Set<Long>? = null
    var mobile: String? = null
    var avatar: String? = null
}
