package dev.jidouka.configuration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import kotlinx.datetime.TimeZone
import org.slf4j.LoggerFactory

/**
 * Configuration for the Jidouka automation library.
 * @property logging Logging configuration for the library
 * @property timeZone Timezone used for all time-based operations
 */
public data class JidoukaConfiguration(
    val logging: Logging = Logging(),
    val timeZone: TimeZone = TimeZone.currentSystemDefault()
) {

    /**
     * Logging configuration for Jidouka.
     *
     * Controls log output level and file rotation settings. Logs are written to files
     * in the specified directory with automatic rotation based on size and count limits.
     *
     * @property logLevel Minimum severity level to log. Defaults to [LogLevel.Info].
     * @property logDirectory Directory where log files will be written.
     * @property maxLogFiles Maximum number of archived log files to keep. When exceeded,
     *                       oldest files are deleted.
     * @property maxFileSize Maximum size of a single log file before rotation.
     * @property maxLogsSize Maximum total size of all log files. When exceeded, oldest files
     *                       are deleted even if under [maxLogFiles] count.
     */
    public data class Logging(
        public val logLevel: LogLevel = LogLevel.Info,
        public val logDirectory: String = "logs",
        public val maxLogFiles: Int = 5,
        public val maxFileSize: String = "10MB",
        public val maxLogsSize: String = "50MB"
    ) {
        public enum class LogLevel {
            Trace,
            Debug,
            Info,
            Warn,
            Error
        }
    }

    public companion object {
        private const val LOGGER_NAME = "dev.jidouka"

        internal fun configureLogging(configuration: JidoukaConfiguration) {
            val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext
                ?: return

            val jidoukaLogger = loggerContext.getLogger(LOGGER_NAME)
            jidoukaLogger.level = Level.toLevel(
                configuration.logging.logLevel.toLogbackLevel().levelStr,
                Level.INFO
            )
        }

        private fun Logging.LogLevel.toLogbackLevel(): Level {
            return when (this) {
                Logging.LogLevel.Trace -> Level.TRACE
                Logging.LogLevel.Debug -> Level.DEBUG
                Logging.LogLevel.Info -> Level.INFO
                Logging.LogLevel.Warn -> Level.WARN
                Logging.LogLevel.Error -> Level.ERROR
            }
        }
    }
}


