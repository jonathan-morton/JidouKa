package dev.jidouka.codegen

import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before

abstract class BaseUnitTest {
    @Before
    open fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    @After
    open fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }
}
