package im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 文件预签名地址 Response VO")
class FilePresignedUrlRespVO {
    @Schema(description = "配置编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "11")
    var configId: Long? = null
    @Schema(description = "文件上传 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    var uploadUrl: String? = null
    @Schema(description = "文件访问 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    var url: String? = null
    @Schema(description = "文件路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "xxx.png")
    var path: String? = null
}
