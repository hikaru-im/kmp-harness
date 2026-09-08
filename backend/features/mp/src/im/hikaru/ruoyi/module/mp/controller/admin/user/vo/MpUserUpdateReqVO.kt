package im.hikaru.ruoyi.module.mp.controller.admin.user.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号粉丝更新 Request VO")
class MpUserUpdateReqVO {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "编号不能为空")
    var id: Long? = null
    @field:Schema(description = "昵称", example = "芋道")
    var nickname: String? = null
    @field:Schema(description = "备注", example = "你是一个芋头嘛")
    var remark: String? = null
    @field:Schema(description = "标签编号数组", example = "1,2,3")
    var tagIds: List<Long>? = null
}
