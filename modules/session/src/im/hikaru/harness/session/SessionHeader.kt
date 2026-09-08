package im.hikaru.harness.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val SESSION_FORMAT_VERSION: Long = 0L

@Serializable
enum class SessionOrigin {
    @SerialName("subagent")
    SUBAGENT,
}

@Serializable
data class SessionHeader(
    val version: Long = SESSION_FORMAT_VERSION,
    val id: SessionId,
    val createdAt: Long,
    val cwd: String? = null,
    val parentSession: SessionId? = null,
    val seedLength: Long? = null,
    val origin: SessionOrigin? = null,
    val delegationDepth: Long? = null,
    val agentPreset: String? = null,
) {
    init {
        require(version >= 0L) {
            "Session header version must not be negative"
        }
        require(createdAt >= 0L) {
            "Session creation time must not be negative"
        }
        require(cwd == null || isPortableAbsolutePath(cwd)) {
            "Session cwd must be an absolute POSIX, drive, or UNC path"
        }
        require(seedLength == null || seedLength >= 0L) {
            "Session seed length must not be negative"
        }
        require(delegationDepth == null || delegationDepth >= 0L) {
            "Session delegation depth must not be negative"
        }
        require(agentPreset == null || agentPreset.isNotBlank()) {
            "Session agent preset must not be blank"
        }
    }
}

data class CreateSessionOptions(
    val cwd: String? = null,
    val parentSession: SessionId? = null,
    val seedLength: Long? = null,
    val origin: SessionOrigin? = null,
    val delegationDepth: Long? = null,
    val agentPreset: String? = null,
) {
    internal fun toHeader(
        id: SessionId,
        createdAt: Long,
    ): SessionHeader =
        try {
            SessionHeader(
                id = id,
                createdAt = createdAt,
                cwd = cwd,
                parentSession = parentSession,
                seedLength = seedLength,
                origin = origin,
                delegationDepth = delegationDepth,
                agentPreset = agentPreset,
            )
        } catch (error: IllegalArgumentException) {
            invalidHeader(
                message = error.message ?: "Invalid Session header",
                cause = error,
            )
        }
}

private val windowsDrivePath =
    Regex("^[A-Za-z]:[\\\\/].+")

private fun isPortableAbsolutePath(path: String): Boolean =
    path.startsWith("/") ||
        path.startsWith("\\\\") ||
        path.startsWith("//") ||
        windowsDrivePath.matches(path)
