package io.smartycoder.bignum.fields

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [withSlotRecovery] is generic over the element type, so it can be driven here on a plain
 * `Flow<String>` -- no Context, no Karoo, no Robolectric. The real flow carries
 * (Frame, Appearance) pairs, but nothing in the operator depends on that.
 */
class SlotRecoveryTest {

    @Test
    fun `a failing slot resubscribes and keeps updating -- catch would complete it instead`() {
        // This is the regression guard for the `.catch` bug. With `catch` the flow would emit
        // the fallback and then COMPLETE, pinning that half of the tile at "--" for the rest of
        // the ride while the other half kept updating. The third element below is what proves
        // the stream came back.
        runTest {
            var collections = 0
            val upstream = flow {
                if (collections++ == 0) throw IllegalStateException("stream dropped")
                emit("real")
            }

            val seen = upstream.withSlotRecovery(fallback = { FALLBACK }, delayMs = 2_000L).toList()

            assertEquals(listOf(FALLBACK, FALLBACK, "real"), seen)
            assertEquals(2, collections)
        }
    }

    @Test
    fun `onStart emits the fallback before the first upstream value`() {
        runTest {
            val seen = flowOf("first").withSlotRecovery(fallback = { FALLBACK }, delayMs = 2_000L).toList()

            assertEquals(FALLBACK, seen.first())
            assertEquals(listOf(FALLBACK, "first"), seen)
        }
    }

    @Test
    fun `a flow that never fails is passed through unchanged apart from the fallback`() {
        runTest {
            val upstream = listOf("a", "b", "c")

            val seen = flowOf(*upstream.toTypedArray())
                .withSlotRecovery(fallback = { FALLBACK }, delayMs = 2_000L)
                .toList()

            assertEquals(upstream, seen.drop(1))
        }
    }

    private companion object {
        const val FALLBACK = "--"
    }
}
