package im.hikaru.ruoyi.module.mp.controller.admin.account.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 公众号账号精简信息 Response VO")
class MpAccountSimpleRespVO {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "公众号名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道源码")
    var name: String? = null
}
