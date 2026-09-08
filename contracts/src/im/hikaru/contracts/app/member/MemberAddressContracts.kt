package im.hikaru.contracts.app.member

import kotlinx.serialization.Serializable

@Serializable
data class MemberAddressResponse(
    val id: Long? = null,
    val name: String? = null,
    val mobile: String? = null,
    val areaId: Long? = null,
    val areaName: String? = null,
    val detailAddress: String? = null,
    val defaultStatus: Boolean? = null,
    val version: Long? = null,
)

@Serializable
data class MemberAddressCreateRequest(
    val name: String,
    val mobile: String,
    val areaId: Long,
    val detailAddress: String,
    val defaultStatus: Boolean,
)

@Serializable
data class MemberAddressUpdateRequest(
    val id: Long,
    val name: String,
    val mobile: String,
    val areaId: Long,
    val detailAddress: String,
    val defaultStatus: Boolean,
)
