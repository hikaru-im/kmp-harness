package im.hikaru.harness.logger

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Context service that exposes kotlin-logging without owning its implementation.
 *
 * Formatting, filtering, appenders, platform output, and backend configuration
 * remain responsibilities of kotlin-logging and the host application.
 */
class LoggerService {

    fun logger(name: String): KLogger {
        require(name.isNotBlank()) {
            "Logger name must not be blank"
        }

        return KotlinLogging.logger(name)
    }
}
