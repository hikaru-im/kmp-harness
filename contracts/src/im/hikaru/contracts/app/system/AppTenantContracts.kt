package im.hikaru.contracts.app.system

import kotlinx.serialization.Serializable

@Serializable
data class AppTenantResponse(
    val id: Long? = null,
    val name: String? = null,
)
