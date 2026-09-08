package im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 公众号模版消息 Response VO")
class MpMessageTemplateRespVO {
    @field:Schema(description = "模版主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "7019")
    var id: Long? = null
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var accountId: Long? = null
    @field:Schema(description = "appId", requiredMode = Schema.RequiredMode.REQUIRED, example = "wx1234567890abcdef")
    var appId: String? = null
    @field:Schema(description = "公众号模板ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "IjkGxO9M_mC9pE5Yl7QYJk1h0Dj2N4lC3oOp6rRsT8u")
    var templateId: String? = null
    @field:Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "订单状态提醒")
    var title: String? = null
    @field:Schema(description = "模板内容", requiredMode = Schema.RequiredMode.REQUIRED)
    var content: String? = null
    @field:Schema(description = "模板示例")
    var example: String? = null
    @field:Schema(description = "模板所属行业的一级行业", example = "电商")
    var primaryIndustry: String? = null
    @field:Schema(description = "模板所属行业的二级行业", example = "商品售后")
    var deputyIndustry: String? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
