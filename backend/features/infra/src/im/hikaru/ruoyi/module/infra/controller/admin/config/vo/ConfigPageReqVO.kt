package im.hikaru.ruoyi.module.infra.controller.admin.config.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 参数配置分页 Request VO")
class ConfigPageReqVO : PageParam() {
    @Schema(description = "数据源名称，模糊匹配", example = "名称")
    var name: String? = null

    @Schema(description = "参数键名，模糊匹配", example = "yunai.db.username")
    var key: String? = null

    @Schema(description = "参数类型", example = "1")
    var type: Int? = null

    @Schema(description = "创建时间")
    var createTime: Array<LocalDateTime>? = null
}
