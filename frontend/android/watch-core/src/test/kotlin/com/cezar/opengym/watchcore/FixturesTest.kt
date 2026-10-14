package com.cezar.opengym.watchcore

import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The contract with upstream's lib/watch-sync.js, from the watch's end (the phone's end is
 * frontend/src/fork/watch-fixtures.test.js). The snapshots were written by upstream's own
 * buildSnapshot; events.json is written here and applied there with upstream's applyEvents.
 * A changed events.json fails this test: rerun with UPDATE_FIXTURES=1, then the vitest file.
 */
class FixturesTest {
    private val dir = File("src/test/resources/fixtures")
    private fun snapshot(name: String): Snapshot =
        Snapshot.parse(File(dir, "snapshots/$name.json").readText()) ?: error("$name does not parse")

    @Test fun everySnapshotUpstreamWritesIsRead() {
        val files = File(dir, "snapshots").listFiles { f -> f.name.endsWith(".json") }!!
        assertTrue("run the vitest fixtures first", files.size >= 4)
        for (f in files) {
            val snap = Snapshot.parse(f.readText())
            assertNotNull(f.name, snap)
            assertTrue(f.name, snap!!.compatible)
        }
    }

    @Test fun noWorkoutIsTheWaitingScreen() {
        val s = SessionCore.applySnapshot(SessionState(), snapshot("no-workout"))
        assertNull(s.workout)
    }

    @Test fun treinoAComesInWhereThePhoneIs() {
        val snap = snapshot("treino-a")
        val s = SessionCore.applySnapshot(SessionState(), snap)
        val w = s.workout!!
        assertEquals(8, w.entries.size)
        assertEquals(1, s.entryIndex)
        assertEquals(1760530290000.0, w.rest!!.endsAt, 0.0)
        assertTrue(w.entries[0].sets[0].warmup)
        assertEquals(2, SessionCore.setIndex(s, 0))
        assertEquals("pt", s.settings.lang)
        assertEquals(66.6, s.settings.lastBw!!, 0.0)
        // two work sets of nine planned are done (the warm-up does not count)
        assertEquals(Progress(1, 25), SessionCore.progress(s))
    }

    @Test fun theSupersetFromThePhoneGoesInTurns() {
        var s = SessionCore.applySnapshot(SessionState(), snapshot("superset"))
        s = SessionCore.tick(s, entry = 0, set = 0, w = 12.0, r = 10.0, at = 1.0, id = "a").first
        assertEquals(Next(1, false), SessionCore.next(s, entry = 0, set = 0))
    }

    @Test fun perSideHoldsAndCardioFromThePhone() {
        val s = SessionCore.applySnapshot(SessionState(), snapshot("per-side-time-cardio"))
        val e = s.workout!!.entries
        assertEquals("lb", s.unit)
        assertEquals("L", e[0].sets[0].sideUp)
        assertTrue(e[1].isTimed)
        assertEquals(45.0, e[1].sets[0].sec!!, 0.0)
        assertTrue(e[2].isCardio)
        assertFalse(e[2].sets[0].done)
    }

    /** A short session on Treino A, as the watch would send it: the file the phone's test applies. */
    @Test fun theEventsTheWatchSendsAreWrittenForThePhone() {
        var s = SessionCore.applySnapshot(SessionState(), snapshot("treino-a"))
        val events = mutableListOf<Event>()
        fun keep(p: Pair<SessionState, Event?>) { s = p.first; p.second?.let(events::add) }
        keep(SessionCore.tick(s, entry = 0, set = 2, w = 42.5, r = 9.0, at = 1760530400000.0, id = "evt-1"))
        keep(SessionCore.tick(s, entry = 1, set = 0, w = 25.0, r = 12.0, at = 1760530500000.0, id = "evt-2"))
        keep(SessionCore.tick(s, entry = 1, set = 1, w = null, r = 11.0, at = 1760530600000.0, id = "evt-3"))
        keep(SessionCore.finish(s, at = 1760531000000.0, id = "evt-4"))
        val json = WatchJson.encodeToString(JsonArray.serializer(), JsonArray(events)) + "\n"
        val file = File(dir, "events.json")
        if (System.getenv("UPDATE_FIXTURES") == "1" || !file.exists()) file.writeText(json)
        assertEquals("events.json is stale: UPDATE_FIXTURES=1 ./gradlew :watch-core:test", file.readText(), json)
    }
}
