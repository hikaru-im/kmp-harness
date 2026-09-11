package im.hikaru.harness.desktop

/** Close every application-owned resource, preserving the first failure. */
internal suspend fun shutdownDesktopResources(
    accountShutdown: suspend () -> Unit,
    hostClose: suspend () -> Unit,
) {
    var failure: Throwable? = null
    try {
        accountShutdown()
    } catch (error: Throwable) {
        failure = error
    }
    try {
        hostClose()
    } catch (error: Throwable) {
        if (failure == null) {
            failure = error
        } else if (failure !== error) {
            failure.addSuppressed(error)
        }
    }
    failure?.let { throw it }
}
