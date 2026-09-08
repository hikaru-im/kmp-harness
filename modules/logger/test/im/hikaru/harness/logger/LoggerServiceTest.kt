package im.hikaru.harness.logger

import im.hikaru.harness.runtime.Runtime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class LoggerServiceTest {

    @Test
    fun shouldDelegateLoggerCreationToKotlinLogging() {
        val logger = LoggerService().logger("agent.worker")

        assertNotNull(logger)
    }

    @Test
    fun shouldRejectBlankLoggerName() {
        assertFailsWith<IllegalArgumentException> {
            LoggerService().logger("  ")
        }
    }

    @Test
    fun pluginShouldBindServiceToFiberLifecycle() = runTest {
        val runtime = Runtime()
        val fiber = runtime.install(LoggerPlugin)
        val service = runtime.context.require(LoggerKey)

        assertSame(service, runtime.context.require(LoggerKey))

        runtime.uninstall(fiber)

        assertFalse(runtime.context.has(LoggerKey))
    }
}
