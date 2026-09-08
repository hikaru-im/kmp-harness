package im.hikaru.contracts.app.system

import kotlinx.serialization.Serializable

@Serializable
data class AppAreaNodeResponse(
    val id: Int? = null,
    val name: String? = null,
    val children: List<AppAreaNodeResponse> = emptyList(),
)

@Serializable
data class AppDictDataResponse(
    val id: Long? = null,
    val label: String? = null,
    val value: String? = null,
    val dictType: String? = null,
)
