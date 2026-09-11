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

/** Acquire engine, client and transport in order, closing every acquired resource on failure. */
internal fun <Engine : AutoCloseable, Client : AutoCloseable, Transport : AutoCloseable, Account> createOwnedAccount(
    createEngine: () -> Engine,
    createClient: (Engine) -> Client,
    createTransport: (Engine, Client) -> Transport,
    createAccount: (Transport) -> Account,
): Account {
    var engine: Engine? = null
    var client: Client? = null
    var transport: Transport? = null
    var ownershipTransferred = false
    return try {
        val acquiredEngine = createEngine()
        engine = acquiredEngine
        val acquiredClient = createClient(acquiredEngine)
        client = acquiredClient
        val acquiredTransport = createTransport(acquiredEngine, acquiredClient)
        transport = acquiredTransport
        ownershipTransferred = true
        createAccount(acquiredTransport)
    } catch (error: Throwable) {
        if (ownershipTransferred) {
            closeAfterFailure(transport, error)
        } else {
            closeAfterFailure(client, error)
            closeAfterFailure(engine, error)
        }
        throw error
    }
}

private fun closeAfterFailure(resource: AutoCloseable?, failure: Throwable) {
    if (resource == null) return
    try {
        resource.close()
    } catch (cleanupError: Throwable) {
        if (cleanupError !== failure) failure.addSuppressed(cleanupError)
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
