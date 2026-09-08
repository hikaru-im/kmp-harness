package im.hikaru.ruoyi.module.infra.controller.admin.db.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 数据源配置创建/修改 Request VO")
class DataSourceConfigSaveReqVO {
    @Schema(description = "主键编号", example = "1024")
    var id: Long? = null

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "test")
    @field:NotNull(message = "数据源名称不能为空")
    var name: String? = null

    @Schema(description = "数据源连接", requiredMode = Schema.RequiredMode.REQUIRED, example = "jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro")
    @field:NotNull(message = "数据源连接不能为空")
    var url: String? = null

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "root")
    @field:NotNull(message = "用户名不能为空")
    var username: String? = null

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @field:NotNull(message = "密码不能为空")
    var password: String? = null
}
