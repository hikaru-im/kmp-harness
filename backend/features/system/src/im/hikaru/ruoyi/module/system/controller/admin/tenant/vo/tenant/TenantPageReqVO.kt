package im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import kotlinx.datetime.LocalDateTime

class TenantPageReqVO : PageParam() { var name: String? = null; var contactName: String? = null; var contactMobile: String? = null; var status: Int? = null; var createTime: List<LocalDateTime>? = null }
