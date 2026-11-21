package dev.jidouka.common.extensions

import dev.jidouka.common.BaseUnitTest
import io.github.oshai.kotlinlogging.KLogger
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.Rule
import org.junit.rules.TestName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class LoggingExtensionsTest : BaseUnitTest() {
    @get:Rule
    val testName = TestName()

    @MockK
    private lateinit var logger: KLogger

    private var loggedLine: Any? = null
    private var loggedException: Throwable? = null

    override fun setUp() {
        super.setUp()
        loggedLine = null
        loggedException = null
        every { logger.isLoggingEnabledFor(any(), any()) } returns true

        // Run the message lambda inside the logger call, as a real logger does
        every { logger.trace(any<() -> Any?>()) } answers { loggedLine = firstArg<() -> Any?>().invoke() }
        every { logger.debug(any<() -> Any?>()) } answers { loggedLine = firstArg<() -> Any?>().invoke() }
        every { logger.info(any<() -> Any?>()) } answers { loggedLine = firstArg<() -> Any?>().invoke() }
        every { logger.warn(any<() -> Any?>()) } answers { loggedLine = firstArg<() -> Any?>().invoke() }
        every { logger.error(any<() -> Any?>()) } answers { loggedLine = firstArg<() -> Any?>().invoke() }
        every { logger.warn(any<Throwable>(), any<() -> Any?>()) } answers {
            loggedException = firstArg()
            loggedLine = secondArg<() -> Any?>().invoke()
        }
        every { logger.error(any<Throwable>(), any<() -> Any?>()) } answers {
            loggedException = firstArg()
            loggedLine = secondArg<() -> Any?>().invoke()
        }
    }

    @Test
    fun `traceF logs the message with the calling method`() {
        logger.traceF { MESSAGE }

        assertEquals(expectedLine(), loggedLine)
    }

    @Test
    fun `debugF logs the message with the calling method`() {
        logger.debugF { MESSAGE }

        assertEquals(expectedLine(), loggedLine)
    }

    @Test
    fun `infoF logs the message with the calling method`() {
        logger.infoF { MESSAGE }

        assertEquals(expectedLine(), loggedLine)
    }

    @Test
    fun `warnF logs the message with the calling method`() {
        logger.warnF { MESSAGE }

        assertEquals(expectedLine(), loggedLine)
    }

    @Test
    fun `warnF with exception logs the message with the calling method and passes the exception`() {
        logger.warnF(exception) { MESSAGE }

        assertEquals(expectedLine(), loggedLine)
        assertSame(exception, loggedException)
    }

    @Test
    fun `errorF logs the message with the calling method`() {
        logger.errorF { MESSAGE }

        assertEquals(expectedLine(), loggedLine)
    }

    @Test
    fun `errorF with exception logs the message with the calling method and passes the exception`() {
        logger.errorF(exception) { MESSAGE }

        assertEquals(expectedLine(), loggedLine)
        assertSame(exception, loggedException)
    }

    @Test
    fun `message is not evaluated when the level is disabled`() {
        every { logger.isLoggingEnabledFor(any(), any()) } returns false
        var evaluated = false
        val buildMessage = {
            evaluated = true
            MESSAGE
        }

        logger.traceF(buildMessage)
        logger.debugF(buildMessage)
        logger.infoF(buildMessage)
        logger.warnF(buildMessage)
        logger.warnF(exception, buildMessage)
        logger.errorF(buildMessage)
        logger.errorF(exception, buildMessage)

        assertFalse(evaluated)
        assertNull(loggedLine)
    }

    private fun expectedLine() = "[${testName.methodName}] $MESSAGE"

    private val exception = IllegalStateException("boom goes the dynamite")

    private companion object {
        const val MESSAGE = "message"
    }
}
