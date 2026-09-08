package im.hikaru.ruoyi.module.mp.controller.admin.material.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 公众号素材上传结果 Response VO")
class MpMaterialUploadRespVO {
    @field:Schema(description = "素材的 media_id", requiredMode = Schema.RequiredMode.REQUIRED, example = "123")
    var mediaId: String? = null
    @field:Schema(description = "素材的 URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn/1.png")
    var url: String? = null
}
