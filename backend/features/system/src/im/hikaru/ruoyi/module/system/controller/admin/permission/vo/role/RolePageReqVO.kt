package im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import kotlinx.datetime.LocalDateTime

class RolePageReqVO : PageParam() { var name: String? = null; var code: String? = null; var status: Int? = null; var createTime: List<LocalDateTime>? = null }
