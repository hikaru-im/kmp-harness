package im.hikaru.harness.desktop

internal data class DesktopResources<Host, Account>(
    val host: Host,
    val account: Account,
)

/** A successfully started Host remains owned even when later client initialization fails. */
internal suspend fun <Host, Account> openDesktopResources(
    startHost: suspend () -> Host,
    createAccount: () -> Account,
    closeHost: suspend (Host) -> Unit,
): DesktopResources<Host, Account> {
    val host = startHost()
    return try {
        DesktopResources(host, createAccount())
    } catch (error: Throwable) {
        try {
            closeHost(host)
        } catch (cleanupError: Throwable) {
            if (cleanupError !== error) error.addSuppressed(cleanupError)
        }
        throw error
    }
}

/** Transfer a newly-created transport to Account, or close it if Account construction fails. */
internal fun <Transport : AutoCloseable, Account> createOwnedAccount(
    createTransport: () -> Transport,
    createAccount: (Transport) -> Account,
): Account {
    val transport = createTransport()
    return try {
        createAccount(transport)
    } catch (error: Throwable) {
        try {
            transport.close()
        } catch (cleanupError: Throwable) {
            if (cleanupError !== error) error.addSuppressed(cleanupError)
        }
        throw error
    }
}

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
