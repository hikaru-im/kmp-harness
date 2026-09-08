package im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 文件分页 Request VO")
class FilePageReqVO : PageParam() {
    @Schema(description = "文件路径，模糊匹配", example = "yudao")
    var path: String? = null
    @Schema(description = "文件类型，模糊匹配", example = "jpg")
    var type: String? = null
    @Schema(description = "创建时间")
    var createTime: Array<LocalDateTime>? = null
}
