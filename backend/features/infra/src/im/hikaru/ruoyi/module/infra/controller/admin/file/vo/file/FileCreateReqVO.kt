package im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 文件创建 Request VO")
class FileCreateReqVO {
    @field:NotNull(message = "文件配置编号不能为空")
    @Schema(description = "文件配置编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "11")
    var configId: Long? = null

    @field:NotNull(message = "文件路径不能为空")
    @Schema(description = "文件路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao.jpg")
    var path: String? = null

    @field:NotNull(message = "原文件名不能为空")
    @Schema(description = "原文件名", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao.jpg")
    var name: String? = null

    @field:NotNull(message = "文件 URL不能为空")
    @Schema(description = "文件 URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn/yudao.jpg")
    var url: String? = null

    @Schema(description = "文件 MIME 类型", example = "application/octet-stream")
    var type: String? = null
}
