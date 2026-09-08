package im.hikaru.contracts.common

import kotlinx.serialization.Serializable

@Serializable
data class PageRequest(
    val pageNo: Int = 1,
    val pageSize: Int = 10,
)

@Serializable
data class SortingField(
    val field: String? = null,
    val order: String? = null,
) {
    companion object {
        const val ORDER_ASC = "asc"
        const val ORDER_DESC = "desc"
    }
}

@Serializable
data class PageResponse<T>(
    val total: Long? = null,
    val list: List<T> = emptyList(),
)
