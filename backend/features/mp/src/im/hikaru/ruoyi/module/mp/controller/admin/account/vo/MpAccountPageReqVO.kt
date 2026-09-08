package im.hikaru.ruoyi.module.mp.controller.admin.account.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 公众号账号分页 Request VO")
class MpAccountPageReqVO : PageParam() {
    @field:Schema(name = "公众号名称", description = "模糊匹配")
    var name: String? = null
    @field:Schema(name = "公众号账号", description = "模糊匹配")
    var account: String? = null
    @field:Schema(name = "公众号 appid", description = "模糊匹配")
    var appId: String? = null
}
