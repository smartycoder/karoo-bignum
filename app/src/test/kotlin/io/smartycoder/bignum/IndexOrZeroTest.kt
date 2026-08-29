package io.smartycoder.bignum

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `indexOrZero` guards the one call `MainActivity` makes into `Spinner.setSelection` with an id
 * that came out of `Settings.hudSlots`. That id should always be in the catalogue -- hudSlots
 * already resolves against it -- but `setSelection(-1)` would leave a spinner showing nothing,
 * so the fallback to 0 is worth pinning down even though MainActivity itself has no JVM test.
 */
class IndexOrZeroTest {

    private val ids = listOf("speed", "hr", "power")

    @Test
    fun `known id returns its position`() {
        assertEquals(1, indexOrZero(ids, "hr"))
    }

    @Test
    fun `first id returns zero`() {
        assertEquals(0, indexOrZero(ids, "speed"))
    }

    @Test
    fun `unknown id falls back to zero rather than -1`() {
        assertEquals(0, indexOrZero(ids, "madeUp"))
    }

    @Test
    fun `empty list falls back to zero`() {
        assertEquals(0, indexOrZero(emptyList(), "speed"))
    }
}
