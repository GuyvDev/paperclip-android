package com.bu.paperclip

import com.bu.paperclip.ui.screens.RunTranscriptState
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
}
