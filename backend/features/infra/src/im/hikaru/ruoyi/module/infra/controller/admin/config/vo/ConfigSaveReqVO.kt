package im.hikaru.ruoyi.module.infra.controller.admin.config.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "管理后台 - 参数配置创建/修改 Request VO")
class ConfigSaveReqVO {
    @Schema(description = "参数配置序号", example = "1024")
    var id: Long? = null

    @Schema(description = "参数分组", requiredMode = Schema.RequiredMode.REQUIRED, example = "biz")
    @field:NotEmpty(message = "参数分组不能为空")
    @field:Size(max = 50, message = "参数名称不能超过 50 个字符")
    var category: String? = null

    @Schema(description = "参数名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "数据库名")
    @field:NotBlank(message = "参数名称不能为空")
    @field:Size(max = 100, message = "参数名称不能超过 100 个字符")
    var name: String? = null

    @Schema(description = "参数键名", requiredMode = Schema.RequiredMode.REQUIRED, example = "yunai.db.username")
    @field:NotBlank(message = "参数键名长度不能为空")
    @field:Size(max = 100, message = "参数键名长度不能超过 100 个字符")
    var key: String? = null

    @Schema(description = "参数键值", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotBlank(message = "参数键值不能为空")
    @field:Size(max = 500, message = "参数键值长度不能超过 500 个字符")
    var value: String? = null

    @Schema(description = "是否可见", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @field:NotNull(message = "是否可见不能为空")
    var visible: Boolean? = null

    @Schema(description = "备注", example = "备注一下很帅气！")
    var remark: String? = null
}
