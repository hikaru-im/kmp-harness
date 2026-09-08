package im.hikaru.ruoyi.framework.common.pojo

import io.swagger.v3.oas.annotations.media.Schema
import java.io.Serializable

@Schema(description = "分页结果")
class PageResult<T>(
    @Schema(description = "总量", requiredMode = Schema.RequiredMode.REQUIRED)
    var total: Long? = null,

    @Schema(description = "数据", requiredMode = Schema.RequiredMode.REQUIRED)
    var list: List<T> = ArrayList(),
) : Serializable {

    constructor(total: Long) : this(list = ArrayList(), total = total)

    companion object {
        fun <T> empty(): PageResult<T> = PageResult(0L)

        fun <T> empty(total: Long): PageResult<T> = PageResult(total)
    }
}
