package im.hikaru.ruoyi.module.member.controller.admin.point.vo.recrod

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 用户积分记录分页 Request VO")
class MemberPointRecordPageReqVO : PageParam() {
    @field:Schema(description = "用户昵称", example = "张三")
    var nickname: String? = null
    @field:Schema(description = "用户编号", example = "123")
    var userId: Long? = null
    @field:Schema(description = "业务类型", example = "1")
    var bizType: Int? = null
    @field:Schema(description = "积分标题", example = "呵呵")
    var title: String? = null
}
