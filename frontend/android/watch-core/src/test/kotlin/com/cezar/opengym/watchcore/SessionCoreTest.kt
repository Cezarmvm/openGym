package com.cezar.opengym.watchcore

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The watch's session against the sync cases (SessionCore's header lists them). Port of upstream's SessionCoreTests.swift. */
class SessionCoreTest {
    private fun row(w: Double, r: Double, done: Boolean = false, at: Double? = null) =
        SetRow(w = w, r = r, sec = null, done = done, warmup = false, at = at)

    private fun workout(id: String, sets: List<SetRow>, cur: Int = 0) = ActiveWorkout(
        id = id, name = "Push", unit = "kg", cur = cur, restSec = 90.0, rest = null,
        entries = listOf(
            Entry(id = "0025", name = "Bench Press", mode = "reps", sets = sets),
            Entry(id = "0426", name = "Overhead Press", mode = "reps", sets = listOf(row(40.0, 8.0))),
        ),
    )

    private fun snap(w: ActiveWorkout?) = Snapshot(v = 1, unit = "kg", restSec = 90.0, active = w)

    @Test fun picksUpAWorkoutRunningOnThePhoneWhenFirstOpened() {
        val s = SessionCore.applySnapshot(SessionState(), snap(workout("p1", listOf(row(60.0, 8.0, done = true, at = 1.0), row(60.0, 8.0)), cur = 1)))
        assertEquals("p1", s.workout?.id)
        assertEquals(1, s.entryIndex)
        assertEquals(1, SessionCore.setIndex(s, 0))
    }

    @Test fun aTickStaysUntilTheSnapshotShowsItAndTheLaterTickWins() {
        var s = SessionCore.applySnapshot(SessionState(), snap(workout("p1", listOf(row(60.0, 8.0), row(60.0, 8.0)))))
        val (t, event) = SessionCore.tick(s, entry = 0, set = 0, w = 62.5, r = 7.0, at = 1000.0, id = "e1")
        s = t
        assertEquals("setDone", event?.get("type")?.jsonPrimitive?.content)
        assertEquals("p1", event?.get("wid")?.jsonPrimitive?.content)
        // an older snapshot, sent before the phone applied the tick: the tick stays
        s = SessionCore.applySnapshot(s, snap(workout("p1", listOf(row(60.0, 8.0), row(60.0, 8.0)))))
        assertEquals(row(62.5, 7.0, done = true, at = 1000.0), s.workout?.entries?.get(0)?.sets?.get(0))
        assertNotNull(s.pending["0025#0"])
        // the phone changed that set itself earlier: the watch's later tick still wins
        s = SessionCore.applySnapshot(s, snap(workout("p1", listOf(row(55.0, 5.0, done = true, at = 500.0), row(60.0, 8.0)))))
        assertEquals(62.5, s.workout!!.entries[0].sets[0].w!!, 0.0)
        // the phone has applied it: confirmed, no longer pending
        s = SessionCore.applySnapshot(s, snap(workout("p1", listOf(row(62.5, 7.0, done = true, at = 1000.0), row(60.0, 8.0)))))
        assertNull(s.pending["0025#0"])
        // a later change on the phone wins over the watch's tick
        s = SessionCore.tick(s, entry = 0, set = 1, w = 60.0, r = 8.0, at = 2000.0, id = "e2").first
        s = SessionCore.applySnapshot(s, snap(workout("p1", listOf(row(62.5, 7.0, done = true, at = 1000.0), row(65.0, 6.0, done = true, at = 3000.0)))))
        assertEquals(65.0, s.workout!!.entries[0].sets[1].w!!, 0.0)
        assertNull(s.pending["0025#1"])
    }

    @Test fun relaunchRestoresTheSessionWithItsUnconfirmedTicks() {
        var s = SessionCore.applySnapshot(SessionState(), snap(workout("p1", listOf(row(60.0, 8.0), row(60.0, 8.0)))))
        s = SessionCore.tick(s, entry = 0, set = 0, w = 60.0, r = 8.0, at = 1000.0, id = "e1").first
        val saved = WatchJson.encodeToString(SessionState.serializer(), s)
        var back = WatchJson.decodeFromString(SessionState.serializer(), saved)
        assertEquals(s, back)
        // the latest snapshot is the old one: the tick survives
        back = SessionCore.applySnapshot(back, snap(workout("p1", listOf(row(60.0, 8.0), row(60.0, 8.0)))))
        assertTrue(back.workout!!.entries[0].sets[0].done)
    }

    @Test fun finishedOnTheWatchStaysFinishedThoughThePhoneHasNotHeard() {
        var s = SessionCore.applySnapshot(SessionState(), snap(workout("p1", listOf(row(60.0, 8.0)))))
        val (f, event) = SessionCore.finish(s, at = 5000.0, id = "f1")
        s = f
        assertEquals("finish", event?.get("type")?.jsonPrimitive?.content)
        assertNull(s.workout)
        s = SessionCore.applySnapshot(s, snap(workout("p1", listOf(row(60.0, 8.0)))))
        assertNull("a snapshot from before the phone applied the finish does not bring it back", s.workout)
        s = SessionCore.applySnapshot(s, snap(workout("p2", listOf(row(60.0, 8.0)))))
        assertEquals("the next workout does show", "p2", s.workout?.id)
    }

    @Test fun finishedOnThePhoneEndsItHere() {
        var s = SessionCore.applySnapshot(SessionState(), snap(workout("p1", listOf(row(60.0, 8.0)))))
        s = SessionCore.applySnapshot(s, snap(null))
        assertNull(s.workout)
        assertTrue(s.pending.isEmpty())
    }

    @Test fun startedOnTheWatchStaysUntilThePhoneHasIt() {
        val ready = ReadySession(routineIds = listOf("r1"), name = "Push", entries = workout("x", listOf(row(60.0, 8.0))).entries)
        var (s, event) = SessionCore.start(SessionState(), ready, wid = "watch-1", at = 100.0, id = "s1")
        assertEquals("start", event["type"]?.jsonPrimitive?.content)
        assertEquals("watch-1", event["wid"]?.jsonPrimitive?.content)
        s = SessionCore.tick(s, entry = 0, set = 0, w = 60.0, r = 8.0, at = 200.0, id = "e1").first
        s = SessionCore.applySnapshot(s, snap(null))
        assertEquals("the phone has not heard of it yet", "watch-1", s.workout?.id)
        s = SessionCore.applySnapshot(s, snap(workout("phone-other", listOf(row(1.0, 1.0)))))
        assertEquals("another workout on the phone does not replace it", "watch-1", s.workout?.id)
        s = SessionCore.applySnapshot(s, snap(workout("watch-1", listOf(row(60.0, 8.0), row(40.0, 8.0)))))
        assertNull("the phone runs it now", s.startedHere)
        assertTrue("with the tick made before it heard", s.workout!!.entries[0].sets[0].done)
    }

    @Test fun anotherWorkoutOnThePhoneStartsClean() {
        var s = SessionCore.applySnapshot(SessionState(), snap(workout("p1", listOf(row(60.0, 8.0)))))
        s = SessionCore.tick(s, entry = 0, set = 0, w = 60.0, r = 8.0, at = 1.0, id = "e1").first
        s = SessionCore.applySnapshot(s, snap(workout("p2", listOf(row(70.0, 5.0)), cur = 1)))
        assertEquals("p2", s.workout?.id)
        assertFalse("an old tick never lands on another workout", s.workout!!.entries[0].sets[0].done)
        assertEquals(1, s.entryIndex)
    }

    @Test fun progressCountsWorkSetsOnly() {
        val w0 = workout("p1", listOf(row(60.0, 8.0, done = true, at = 1.0), row(60.0, 8.0)))
        val e0 = w0.entries[0]
        val w = w0.copy(entries = listOf(e0.copy(sets = listOf(SetRow(w = 20.0, r = 10.0, done = true, warmup = true, at = 1.0)) + e0.sets)) + w0.entries.drop(1))
        val s = SessionCore.applySnapshot(SessionState(), snap(w))
        assertEquals(1, SessionCore.progress(s).done)
        assertEquals(3, SessionCore.progress(s).total)
    }

    @Test fun readsASnapshotWithFieldsItDoesNotKnowAndWithoutOptionalOnes() {
        val json = """{"v":1,"future":true,"active":{"id":"p1","name":"Push","unit":"kg","cur":0,"restSec":90,"entries":[{"id":"0025","name":"Bench","mode":"reps","sets":[{"w":60,"r":8,"done":false,"warmup":false,"hr":{"avg":1}}]}]}}"""
        val snap = Snapshot.parse(json)!!
        assertEquals(1, SessionCore.applySnapshot(SessionState(), snap).workout?.entries?.first()?.sets?.size)
    }

    @Test fun anIncompatibleVersionIsKnownAsSuch() {
        assertFalse(Snapshot.parse("""{"v":2}""")!!.compatible)
        assertTrue(Snapshot.parse("""{"v":1}""")!!.compatible)
        assertNull(Snapshot.parse("not json"))
    }

    @Test fun eventsCarryOnlyTheFieldsThatApply() {
        val s = SessionCore.applySnapshot(SessionState(), snap(workout("p1", listOf(row(60.0, 8.0)))))
        val e = SessionCore.tick(s, entry = 0, set = 0, w = 60.0, r = 8.0, at = 1.0, id = "e1").second!!
        assertEquals(setOf("id", "type", "wid", "exId", "entryIdx", "setIdx", "at", "w", "r"), e.keys)
        assertEquals(JsonPrimitive(0), e["entryIdx"])
    }
}
