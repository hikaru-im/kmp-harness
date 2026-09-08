package im.hikaru.ruoyi.module.member.controller.admin.point.vo.recrod

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 用户积分记录 Response VO")
class MemberPointRecordRespVO {
    @field:Schema(description = "自增主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "31457")
    var id: Long? = null
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var userId: Long? = null
    @field:Schema(description = "昵称", example = "张三")
    var nickname: String? = null
    @field:Schema(description = "业务编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "22706")
    var bizId: String? = null
    @field:Schema(description = "业务类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var bizType: Int? = null
    @field:Schema(description = "积分标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "你猜")
    var title: String? = null
    @field:Schema(description = "积分描述", example = "你猜")
    var description: String? = null
    @field:Schema(description = "积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var point: Int? = null
    @field:Schema(description = "变动后的积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "200")
    var totalPoint: Int? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
