package im.hikaru.contracts.infra

import kotlinx.serialization.Serializable

@Serializable
data class FileInfo(
    val id: Long? = null,
    val configId: Long? = null,
    val path: String? = null,
    val name: String? = null,
    val url: String? = null,
    val type: String? = null,
    val size: Long? = null,
    val createTime: String? = null,
)

@Serializable
data class FilePresignedUrl(
    val configId: Long? = null,
    val uploadUrl: String? = null,
    val url: String? = null,
    val path: String? = null,
)

@Serializable
data class ConfigInfo(
    val id: Long? = null,
    val category: String? = null,
    val name: String? = null,
    val key: String? = null,
    val value: String? = null,
    val type: Int? = null,
    val visible: Boolean? = null,
    val remark: String? = null,
    val createTime: String? = null,
)

@Serializable
data class RedisMonitorInfo(
    val info: Map<String, String> = emptyMap(),
    val dbSize: Long? = null,
    val commandStats: List<RedisCommandStat> = emptyList(),
)

@Serializable
data class RedisCommandStat(
    val command: String? = null,
    val calls: Long? = null,
    val usec: Long? = null,
)
