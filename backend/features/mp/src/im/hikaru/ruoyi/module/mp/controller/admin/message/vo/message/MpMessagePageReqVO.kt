package im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 公众号消息分页 Request VO")
class MpMessagePageReqVO : PageParam() {
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "公众号账号的编号不能为空")
    var accountId: Long? = null
    @field:Schema(description = "消息类型 参见 WxConsts.XmlMsgType 枚举", example = "text")
    var type: String? = null
    @field:Schema(description = "公众号粉丝标识", example = "o6_bmjrPTlm6_2sgVt7hMZOPfL2M")
    var openid: String? = null
    @field:Schema(description = "公众号粉丝 UserId", example = "1")
    var userId: String? = null
    @field:DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @field:Schema(description = "创建时间")
    var createTime: Array<LocalDateTime>? = null
}
