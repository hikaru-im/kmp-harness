package im.hikaru.ruoyi.framework.common.pojo

import java.io.Serializable

/**
 * 排序字段 DTO
 *
 * 类名加了 ing 的原因是，避免和 ES SortField 重名。
 */
data class SortingField(
    var field: String? = null,
    var order: String? = null,
) : Serializable {
    companion object {
        /** 顺序 - 升序 */
        const val ORDER_ASC = "asc"
        /** 顺序 - 降序 */
        const val ORDER_DESC = "desc"
    }
}
