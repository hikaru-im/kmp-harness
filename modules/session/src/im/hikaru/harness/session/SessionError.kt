package im.hikaru.harness.session

/** Stable Session error codes. Callers must not branch on exception messages. */
object SessionErrorCode {
    const val INVALID_HEADER = "INVALID_HEADER"
    const val INVALID_EVENT = "INVALID_EVENT"
    const val DUPLICATE_ID = "DUPLICATE_ID"
    const val NOT_LIVE = "NOT_LIVE"
    const val REENTRANT_LIFECYCLE = "REENTRANT_LIFECYCLE"
    const val CLOSED_LIFECYCLE = "CLOSED_LIFECYCLE"
    const val UNSUPPORTED_SURFACE_OPERATION = "UNSUPPORTED_SURFACE_OPERATION"
    const val STORE_DISPOSED = "STORE_DISPOSED"
}

class SessionException(
    message: String,
    val code: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    init {
        require(message.isNotBlank()) {
            "SessionException message must not be blank"
        }
        require(code.isNotBlank()) {
            "SessionException code must not be blank"
        }
    }
}

internal fun invalidHeader(
    message: String,
    cause: Throwable? = null,
): Nothing =
    throw SessionException(
        message = message,
        code = SessionErrorCode.INVALID_HEADER,
        cause = cause,
    )

internal fun invalidEvent(
    message: String,
    cause: Throwable? = null,
): Nothing =
    throw SessionException(
        message = message,
        code = SessionErrorCode.INVALID_EVENT,
        cause = cause,
    )
