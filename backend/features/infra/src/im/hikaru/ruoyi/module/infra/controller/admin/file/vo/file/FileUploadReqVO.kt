package im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file

import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FilePathUtils
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import org.springframework.web.multipart.MultipartFile

@Schema(description = "管理后台 - 上传文件 Request VO")
class FileUploadReqVO {
    @Schema(description = "文件附件", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "文件附件不能为空")
    var file: MultipartFile? = null

    @Schema(description = "文件目录", example = "XXX/YYY")
    var directory: String? = null

    companion object {
        @JvmStatic
        fun isDirectoryValid(directory: String?): Boolean =
            FilePathUtils.isDirectoryValid(directory)
    }
}
