package im.hikaru.ruoyi.module.infra.controller.admin.config.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 参数配置信息 Response VO")
class ConfigRespVO {
    @Schema(description = "参数配置序号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null

    @Schema(description = "参数分类", requiredMode = Schema.RequiredMode.REQUIRED, example = "biz")
    var category: String? = null

    @Schema(description = "参数名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "数据库名")
    var name: String? = null

    @Schema(description = "参数键名", requiredMode = Schema.RequiredMode.REQUIRED, example = "yunai.db.username")
    var key: String? = null

    @Schema(description = "参数键值", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var value: String? = null

    @Schema(description = "参数类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var type: Int? = null

    @Schema(description = "是否可见", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    var visible: Boolean? = null

    @Schema(description = "备注", example = "备注一下很帅气！")
    var remark: String? = null

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
