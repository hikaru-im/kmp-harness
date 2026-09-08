package im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Admin dictionary data response")
class DictDataRespVO {
    var id: Long? = null
    var sort: Int? = null
    var label: String? = null
    var value: String? = null
    var dictType: String? = null
    var status: Int? = null
    var colorType: String? = null
    var cssClass: String? = null
    var remark: String? = null
    var createTime: LocalDateTime? = null
}
