package im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 文件 Response VO")
class FileRespVO {
    @Schema(description = "文件编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @Schema(description = "配置编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "11")
    var configId: Long? = null
    @Schema(description = "文件路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao.jpg")
    var path: String? = null
    @Schema(description = "原文件名", example = "yudao.jpg")
    var name: String? = null
    @Schema(description = "文件 URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn/yudao.jpg")
    var url: String? = null
    @Schema(description = "文件MIME类型", example = "application/octet-stream")
    var type: String? = null
    @Schema(description = "文件大小", example = "2048")
    var size: Long? = null
    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
