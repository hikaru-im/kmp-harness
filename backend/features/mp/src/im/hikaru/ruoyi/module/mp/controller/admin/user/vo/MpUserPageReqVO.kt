package im.hikaru.ruoyi.module.mp.controller.admin.user.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号粉丝分页 Request VO")
class MpUserPageReqVO : PageParam() {
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @field:NotNull(message = "公众号账号的编号不能为空")
    var accountId: Long? = null
    @field:Schema(description = "公众号粉丝标识，模糊匹配", example = "o6_bmjrPTlm6_2sgVt7hMZOPfL2M")
    var openid: String? = null
    @field:Schema(description = "微信生态唯一标识，模糊匹配", example = "o6_bmjrPTlm6_2sgVt7hMZOPfL2M")
    var unionId: String? = null
    @field:Schema(description = "公众号粉丝昵称，模糊匹配", example = "芋艿")
    var nickname: String? = null
}
