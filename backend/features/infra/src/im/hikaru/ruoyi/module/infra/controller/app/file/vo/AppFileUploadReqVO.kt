package im.hikaru.ruoyi.module.infra.controller.app.file.vo

import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FileUploadReqVO
import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import org.springframework.web.multipart.MultipartFile

@Schema(description = "App file upload request")
class AppFileUploadReqVO {
    @field:NotNull(message = "File must not be null")
    @Schema(description = "File content", requiredMode = Schema.RequiredMode.REQUIRED)
    var file: MultipartFile? = null

    @Schema(description = "Optional upload directory", example = "images/avatars")
    var directory: String? = null

    @get:AssertTrue(message = "Invalid file directory")
    @get:JsonIgnore
    val directoryValid: Boolean
        get() = FileUploadReqVO.isDirectoryValid(directory)
}
