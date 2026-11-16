package n7.glyph.ui.toy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePacerTest {

    // Feeds [seconds] of vsyncs at [hz] (exact times, as a display gives them) and returns the dtMs of every frame.
    private fun run(hz: Int, seconds: Int, start: Long = 123_456_789L): List<Long> {
        val pacer = FramePacer(start)
        val dts = mutableListOf<Long>()
        for (i in 1..hz.toLong() * seconds) {
            val dt = pacer.next(start + i * 1_000_000_000L / hz)
            if (dt >= 0) dts += dt
        }
        return dts
    }

    @Test
    fun stepsAddUpToTheRealTimeAtEveryDisplayRate() {
        for (hz in listOf(60, 90, 120, 144, 165)) {
            val dts = run(hz, 60)
            val total = dts.sum()
            // At most one frame (and the rest under 1 ms) still owed at the end, never a percent lost.
            assertTrue("$hz Hz: $total ms in 60 s", total in 60_000L - 40L..60_000L)
            assertTrue("$hz Hz: ${dts.size} frames", dts.size in 1_650..2_000)
            assertTrue("$hz Hz", dts.all { it in 29L..40L })
        }
    }

    @Test
    fun sixtyHertzGivesThirtyThreeThirtyThreeThirtyFour() {
        assertEquals(listOf(33L, 33L, 34L, 33L, 33L, 34L), run(60, 1, start = 0L).take(6))
    }

    @Test
    fun aStallIsCappedAndDoesNotCauseACatchUpBurst() {
        val pacer = FramePacer(0L)
        assertEquals(-1L, pacer.next(28_999_999L))
        assertEquals(29L, pacer.next(29_000_000L))
        assertEquals(250L, pacer.next(2_029_500_000L))
        // The next frame is due a normal step later, measured from the stalled frame.
        assertEquals(-1L, pacer.next(2_029_500_000L + 28_000_000L))
        assertEquals(29L, pacer.next(2_029_500_000L + 29_000_000L))
    }
}
