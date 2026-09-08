package im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config

import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientConfig
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Admin - file configuration response")
class FileConfigRespVO {
    var id: Long? = null
    var name: String? = null
    var storage: Int? = null
    var config: FileClientConfig? = null
    var master: Boolean? = null
    var remark: String? = null
    var createTime: LocalDateTime? = null
}
