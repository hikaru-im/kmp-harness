package im.hikaru.ruoyi.module.mp.controller.admin.tag.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

@Schema(description = "管理后台 - 公众号标签分页 Request VO")
class MpTagPageReqVO : PageParam() {
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @field:NotEmpty(message = "公众号账号的编号不能为空")
    var accountId: Long? = null
    @field:Schema(description = "标签名，模糊匹配", example = "哈哈")
    var name: String? = null
}
