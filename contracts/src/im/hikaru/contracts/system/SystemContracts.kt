package im.hikaru.contracts.system

import kotlinx.serialization.Serializable

@Serializable
data class DictDataSummary(
    val dictType: String? = null,
    val value: String? = null,
    val label: String? = null,
    val colorType: String? = null,
    val cssClass: String? = null,
)

@Serializable
data class DictTypeSummary(
    val id: Long? = null,
    val name: String? = null,
    val type: String? = null,
)

@Serializable
data class UserSummary(
    val id: Long? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val sex: Int? = null,
    val deptId: Long? = null,
    val deptName: String? = null,
)

@Serializable
data class DepartmentSummary(
    val id: Long? = null,
    val name: String? = null,
    val parentId: Long? = null,
)

@Serializable
data class PostSummary(
    val id: Long? = null,
    val name: String? = null,
)

@Serializable
data class RoleSummary(
    val id: Long? = null,
    val name: String? = null,
)
