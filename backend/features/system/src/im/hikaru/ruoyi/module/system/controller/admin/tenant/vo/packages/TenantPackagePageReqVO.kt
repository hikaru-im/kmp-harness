package im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.packages

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import kotlinx.datetime.LocalDateTime

class TenantPackagePageReqVO : PageParam() { var name: String? = null; var status: Int? = null; var remark: String? = null; var createTime: List<LocalDateTime>? = null }
