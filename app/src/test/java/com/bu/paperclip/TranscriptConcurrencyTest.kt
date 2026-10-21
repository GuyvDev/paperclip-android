package com.bu.paperclip

import com.bu.paperclip.ui.screens.RunTranscriptState
import com.bu.paperclip.ui.screens.RunLogCatchUp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TranscriptConcurrencyTest {
    @Test fun longTranscriptHasBoundedVisibleHistory() = runBlocking {
        val state = RunTranscriptState(null)
        repeat(5100) { state.feedLive(null, "stderr", "line-$it", it.toLong()) }
        val snapshot = state.snapshot()!!
        assertEquals(5000, snapshot.first.size)
        assertEquals(5000, snapshot.second.size)
        assertTrue(state.trimmed)
        assertEquals("line-100", snapshot.second.first().text)
    }

    @Test fun liveChunksAndSnapshotsRemainConsistent() = runBlocking {
        val state = RunTranscriptState(null)
        coroutineScope {
            repeat(200) { seq -> launch { state.feedLive(null, "stderr", "line-$seq", seq.toLong()) } }
            launch { repeat(20) { state.snapshot(); yield() } }
        }
        state.feedLive(null, "stderr", "line-0", 0) // duplicate after reconnect
        val snapshot = state.snapshot()!!
        assertEquals(200, snapshot.first.size)
        assertEquals(200, snapshot.second.size)
        assertNull(state.snapshot()) // unchanged data does not allocate another snapshot
    }
    @Test fun liveArrivalsWaitUntilSuspendingReplayFinishes() = runTest {
        val catchUp = RunLogCatchUp()
        fun record(id: Int) = buildJsonObject { put("seq", id) }
        catchUp.offer(record(1)); catchUp.offer(record(2))
        val paused = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        val replay = launch(start = CoroutineStart.UNDISPATCHED) {
            catchUp.complete(replay = {
                val id = it.getValue("seq").jsonPrimitive.int
                if (id == 1) paused.await()
                seen.add(id)
            }, recover = { fail("Unexpected overflow") })
        }
        assertTrue(catchUp.offer(record(3)))
        paused.complete(Unit)
        replay.join()
        assertEquals(listOf(1, 2, 3), seen)
        assertFalse(catchUp.offer(record(4)))
    }

    @Test fun overflowRecoversTheGapBeforeNewerLiveRecords() = runTest {
        val catchUp = RunLogCatchUp(capacity = 2)
        fun record(id: Int) = buildJsonObject { put("seq", id) }
        catchUp.offer(record(1)); catchUp.offer(record(2)); catchUp.offer(record(3))
        val paused = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        var recoveries = 0
        val replay = launch(start = CoroutineStart.UNDISPATCHED) {
            catchUp.complete(replay = {
                val id = it.getValue("seq").jsonPrimitive.int
                if (id == 1) paused.await()
                seen.add(id)
            }, recover = {
                recoveries++
                seen.addAll(listOf(3, 4))
                assertTrue(catchUp.offer(record(5)))
            })
        }
        assertTrue(catchUp.offer(record(4)))
        paused.complete(Unit)
        replay.join()
        assertEquals(listOf(1, 2, 3, 4, 5), seen)
        assertEquals(1, recoveries)
    }

}
