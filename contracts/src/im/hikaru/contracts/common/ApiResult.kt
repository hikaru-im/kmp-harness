package im.hikaru.contracts.common

import kotlinx.serialization.Serializable

@Serializable
data class ApiResult<T>(
    val code: Int? = null,
    val msg: String? = null,
    val data: T? = null,
) {
    val isSuccess: Boolean
        get() = code == SUCCESS_CODE

    companion object {
        const val SUCCESS_CODE = 0
    }
}
