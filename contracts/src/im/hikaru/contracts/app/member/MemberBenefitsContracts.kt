package im.hikaru.contracts.app.member

import im.hikaru.contracts.common.PageResponse
import kotlinx.serialization.Serializable

/** Wire shape returned by GET /app-api/member/experience-record/page. */
@Serializable
data class MemberExperienceRecordResponse(
    val title: String? = null,
    val experience: Int? = null,
    val description: String? = null,
    val createTime: Long? = null,
)

/** Wire shape returned by GET /app-api/member/point/record/page. */
@Serializable
data class MemberPointRecordResponse(
    val id: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val point: Int? = null,
    val createTime: Long? = null,
)

/** Query parameters accepted by the member point record endpoint. */
@Serializable
data class MemberPointRecordQuery(
    val pageNo: Int = 1,
    val pageSize: Int = 20,
    val addStatus: Boolean? = null,
)

/** Wire shape returned by GET /app-api/member/sign-in/config/list. */
@Serializable
data class MemberSignInConfigResponse(
    val day: Int? = null,
    val point: Int? = null,
)

/** Wire shape returned by GET /app-api/member/sign-in/record/get-summary. */
@Serializable
data class MemberSignInSummaryResponse(
    val totalDay: Int? = null,
    val continuousDay: Int? = null,
    val todaySignIn: Boolean? = null,
)

/** Wire shape returned by GET/POST /app-api/member/sign-in/record. */
@Serializable
data class MemberSignInRecordResponse(
    val id: Long? = null,
    val day: Int? = null,
    val point: Int? = null,
    val experience: Int? = null,
    val createTime: Long? = null,
)

typealias MemberExperienceRecordPage = PageResponse<MemberExperienceRecordResponse>
typealias MemberPointRecordPage = PageResponse<MemberPointRecordResponse>
typealias MemberSignInRecordPage = PageResponse<MemberSignInRecordResponse>
