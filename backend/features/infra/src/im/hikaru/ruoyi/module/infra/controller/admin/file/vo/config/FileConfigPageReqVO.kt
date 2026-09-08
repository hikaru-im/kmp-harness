package im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

@Schema(description = "管理后台 - 文件配置分页 Request VO")
class FileConfigPageReqVO : PageParam() {
    @Schema(description = "配置名", example = "S3")
    var name: String? = null
    @Schema(description = "存储器", example = "1")
    var storage: Int? = null

    @Schema(description = "Creation time range")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var createTime: Array<LocalDateTime>? = null
}
