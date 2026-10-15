package apincer.android.mmate.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

class VolumeCoalescerTest {

    private var now = 0L
    private val tasks = mutableListOf<Pair<Long, FutureTask<Unit>>>() // due time to task
    private val sent = mutableListOf<Int>()

    private val coalescer = VolumeCoalescer(
        { task, delayMs -> FutureTask(task, Unit).also { tasks += (now + delayMs) to it } as Future<*> },
        { now },
        { _, volume -> sent += volume }
    )

    /** Moves the clock and runs every task that has come due and was not cancelled. */
    private fun advanceTo(time: Long) {
        while (true) {
            val due = tasks.filter { it.first <= time && !it.second.isCancelled && !it.second.isDone }
                .minByOrNull { it.first } ?: break
            now = due.first
            due.second.run()
        }
        now = time
    }

    @Test
    fun drag_sendsAtMostOncePerWindow_andAlwaysTheLastValue() {
        for (step in 0..20) { // a 1 s drag, one value every 50 ms
            advanceTo(step * 50L)
            coalescer.submit("r", 40 + step, false)
        }
        advanceTo(2_000)
        assertEquals(listOf(40, 45, 51, 57, 60), sent)
    }

    @Test
    fun fragileRenderer_getsOneCommandAfterTheValueSettles() {
        for (step in 0..20) {
            advanceTo(step * 50L)
            coalescer.submit("r", 40 + step, true)
        }
        advanceTo(2_000)
        assertEquals(listOf(60), sent)
    }

    @Test
    fun valueSubmittedWhileSending_isNotLost() {
        val reentrant = mutableListOf<Int>()
        lateinit var c: VolumeCoalescer
        c = VolumeCoalescer(
            { task, delayMs -> FutureTask(task, Unit).also { tasks += (now + delayMs) to it } as Future<*> },
            { now },
            { _, volume ->
                reentrant += volume
                if (volume == 10) c.submit("r", 11, false) // the slider moves during the send
            }
        )
        c.submit("r", 10, false)
        advanceTo(1_000)
        assertEquals(listOf(10, 11), reentrant)
    }
}
