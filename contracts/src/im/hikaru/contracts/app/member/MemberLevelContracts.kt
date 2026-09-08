package im.hikaru.contracts.app.member

import kotlinx.serialization.Serializable

/** The network shape exposed by GET /app-api/member/level/list. */
@Serializable
data class MemberLevelResponse(
    val name: String? = null,
    val level: Int? = null,
    val experience: Int? = null,
    val discountPercent: Int? = null,
    val icon: String? = null,
    val backgroundUrl: String? = null,
)
