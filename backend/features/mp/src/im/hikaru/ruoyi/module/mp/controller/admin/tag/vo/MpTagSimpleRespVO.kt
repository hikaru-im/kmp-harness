package im.hikaru.ruoyi.module.mp.controller.admin.tag.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 公众号标签精简信息 Response VO")
class MpTagSimpleRespVO {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "公众号的标签编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    var tagId: Long? = null
    @field:Schema(description = "标签名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "快乐")
    var name: String? = null
}
