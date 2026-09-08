package im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(description = "Admin - create or update file configuration")
class FileConfigSaveReqVO {
    @Schema(description = "Configuration id", example = "1")
    var id: Long? = null

    @field:NotBlank(message = "Configuration name must not be blank")
    @Schema(description = "Configuration name", requiredMode = Schema.RequiredMode.REQUIRED, example = "S3")
    var name: String? = null

    @field:NotNull(message = "Storage type must not be null")
    @Schema(description = "Storage type", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    var storage: Int? = null

    @field:NotNull(message = "Storage configuration must not be null")
    @Schema(description = "Storage configuration", requiredMode = Schema.RequiredMode.REQUIRED)
    var config: Map<String, Any?>? = null

    @Schema(description = "Remark")
    var remark: String? = null
}
