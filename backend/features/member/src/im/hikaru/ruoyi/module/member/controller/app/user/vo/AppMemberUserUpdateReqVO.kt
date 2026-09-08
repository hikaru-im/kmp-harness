package im.hikaru.ruoyi.module.member.controller.app.user.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL

@Schema(description = "用户 App - 会员用户更新 Request VO")
class AppMemberUserUpdateReqVO {
    @field:Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    var nickname: String? = null
    @field:Schema(description = "头像", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn/x.png")
    @URL(message = "头像必须是 URL 格式")
    var avatar: String? = null
    @field:Schema(description = "邮箱", example = "member@iocoder.cn")
    @field:Email(message = "邮箱格式不正确")
    @field:Size(max = 50, message = "邮箱长度不能超过 50 个字符")
    var email: String? = null
    @field:Schema(description = "性别", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var sex: Int? = null
}
