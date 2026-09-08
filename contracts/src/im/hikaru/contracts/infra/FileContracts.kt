package im.hikaru.contracts.infra

import kotlinx.serialization.Serializable

/** 文件信息的跨平台响应模型。 */
@Serializable
public data class FileInfo(
    val id: Long? = null,
    val configId: Long? = null,
    val path: String? = null,
    val name: String? = null,
    val url: String? = null,
    val type: String? = null,
    val size: Long? = null,
    val createTime: String? = null,
)

/** 文件预签名地址的跨平台响应模型。 */
@Serializable
public data class FilePresignedUrl(
    val configId: Long? = null,
    val uploadUrl: String? = null,
    val url: String? = null,
    val path: String? = null,
)
