package com.cezar.opengym.watchcore

import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every kind of set the phone can send, through the watch's session: holds, bodyweight, per-side,
 * warm-ups, supersets, skipped sets, cardio; and the summary at the end. Port of upstream's
 * SetTypesTests.swift (the Apple Health deletion tests are left out with the functions).
 */
class SetTypesTest {
    private fun row(w: Double?, r: Double?, sec: Double? = null, done: Boolean = false, warmup: Boolean = false, at: Double? = null) =
        SetRow(w = w, r = r, sec = sec, done = done, warmup = warmup, at = at)

    private fun sideRow(w: Double, r: Double) =
        row(w, r * 2).copy(sides = Sides(L = SideRow(w, r, false), R = SideRow(w, r, false)))

    private fun entry(id: String, name: String, mode: String = "reps", sets: List<SetRow>, group: String? = null) =
        Entry(id = id, name = name, mode = mode, sets = sets, group = group)

    private fun snapOf(w: ActiveWorkout?) = Snapshot(v = 1, unit = "kg", restSec = 90.0, active = w)

    private fun state(entries: List<Entry>, start: Double? = 0.0): SessionState {
        val w = ActiveWorkout(id = "w1", name = "Mixed", unit = "kg", cur = 0, restSec = 90.0, rest = null, entries = entries, start = start)
        return SessionCore.applySnapshot(SessionState(), snapOf(w))
    }

    private fun num(e: Event?, k: String): Double? = e?.get(k)?.jsonPrimitive?.doubleOrNull

    @Test fun aHoldIsTickedWithItsSeconds() {
        val s0 = state(listOf(entry("plank", "Plank", mode = "time", sets = listOf(row(null, null, sec = 45.0), row(null, null, sec = 45.0)))))
        assertTrue(s0.workout!!.entries[0].isTimed)
        val (s, ev) = SessionCore.tick(s0, entry = 0, set = 0, w = null, r = null, sec = 38.0, at = 10.0, id = "h1")
        assertEquals(38.0, s.workout!!.entries[0].sets[0].sec!!, 0.0)
        assertEquals(38.0, num(ev, "sec")!!, 0.0)
        assertNull(ev!!["w"])
        assertNull(ev["r"])
        // an old snapshot keeps the watch's seconds until the phone has them
        val back = SessionCore.applySnapshot(s, snapOf(s0.workout))
        assertEquals(38.0, back.workout!!.entries[0].sets[0].sec!!, 0.0)
        assertEquals(1, SessionCore.setIndex(back, 0))
    }

    @Test fun bodyweightCountsOnlyTheAddedWeight() {
        val e = entry("pullup", "Pull-up", sets = listOf(row(0.0, 10.0), row(5.0, 8.0))).copy(bodyweight = true)
        var s = state(listOf(e))
        assertTrue(s.workout!!.entries[0].isBodyweight)
        s = SessionCore.tick(s, entry = 0, set = 0, w = 0.0, r = 10.0, at = 1.0, id = "a").first
        s = SessionCore.tick(s, entry = 0, set = 1, w = 5.0, r = 8.0, at = 2.0, id = "b").first
        val sum = SessionCore.summary(s.workout!!, end = 60_000.0)
        assertEquals(2, sum.sets)
        assertEquals(40.0, sum.volume, 0.0)
    }

    @Test fun aPerSideSetIsDoneOneSideAtATime() {
        val e = entry("split", "Split squat", sets = listOf(sideRow(20.0, 8.0), sideRow(20.0, 8.0))).copy(perSide = true)
        var s = state(listOf(e, entry("calf", "Calf raise", sets = listOf(row(40.0, 12.0)))))
        assertEquals("L", s.workout!!.entries[0].sets[0].sideUp)
        val (s1, ev) = SessionCore.tick(s, entry = 0, set = 0, side = "L", w = 20.0, r = 8.0, at = 5.0, id = "l")
        assertEquals("L", ev!!["side"]!!.jsonPrimitive.content)
        assertFalse("one side is not the set", s1.workout!!.entries[0].sets[0].done)
        assertEquals("R", s1.workout!!.entries[0].sets[0].sideUp)
        // the other side next, on the same exercise, without a rest between
        val n = SessionCore.next(s1, entry = 0, set = 0)
        assertEquals(0, n.entry)
        assertFalse(n.rest)
        s = SessionCore.tick(s1, entry = 0, set = 0, side = "R", w = 20.0, r = 7.0, at = 6.0, id = "r").first
        val row0 = s.workout!!.entries[0].sets[0]
        assertTrue(row0.done)
        assertEquals(15.0, row0.r!!, 0.0)
        assertEquals(1, SessionCore.setIndex(s, 0))
        // an old snapshot keeps both sides the watch did
        val back = SessionCore.applySnapshot(s, snapOf(state(listOf(e)).workout))
        assertEquals(7.0, back.workout!!.entries[0].sets[0].sides!!.R.r!!, 0.0)
        assertTrue(back.workout!!.entries[0].sets[0].done)
        // and the phone having them confirms them
        val phone0 = state(listOf(e)).workout!!
        val phone = phone0.copy(entries = listOf(phone0.entries[0].copy(sets = listOf(row0) + phone0.entries[0].sets.drop(1))))
        val confirmed = SessionCore.applySnapshot(back, snapOf(phone))
        assertNull(confirmed.pending["split#0#L"])
        assertNull(confirmed.pending["split#0#R"])
        assertEquals(300.0, SessionCore.summary(s.workout!!, end = 1.0).volume, 0.0)   // 20×8 + 20×7
    }

    @Test fun warmUpsAreNotWorkSets() {
        var s = state(listOf(entry("bench", "Bench", sets = listOf(row(40.0, 10.0, warmup = true), row(60.0, 8.0)))))
        assertEquals(1, SessionCore.progress(s).total)
        s = SessionCore.tick(s, entry = 0, set = 0, w = 40.0, r = 10.0, at = 1.0, id = "w").first
        assertEquals(0, SessionCore.progress(s).done)
        assertEquals(0.0, SessionCore.summary(s.workout!!, end = 1.0).volume, 0.0)
        assertEquals(1, SessionCore.setIndex(s, 0))
    }

    @Test fun aSupersetGoesInTurnsAndRestsAfterTheRound() {
        val s0 = state(listOf(
            entry("curl", "Curl", sets = listOf(row(12.0, 10.0), row(12.0, 10.0)), group = "a"),
            entry("push", "Pushdown", sets = listOf(row(20.0, 12.0), row(20.0, 12.0)), group = "a"),
            entry("raise", "Lateral raise", sets = listOf(row(8.0, 15.0))),
        ))
        var s = SessionCore.tick(s0, entry = 0, set = 0, w = 12.0, r = 10.0, at = 1.0, id = "1").first
        var n = SessionCore.next(s, entry = 0, set = 0)
        assertEquals("on to the partner", 1, n.entry)
        assertFalse("no rest inside a round", n.rest)
        s = SessionCore.tick(s, entry = 1, set = 0, w = 20.0, r = 12.0, at = 2.0, id = "2").first
        n = SessionCore.next(s, entry = 1, set = 0)
        assertEquals("back to the first for the next round", 0, n.entry)
        assertTrue("the rest after the round", n.rest)
        s = SessionCore.tick(s, entry = 0, set = 1, w = 12.0, r = 10.0, at = 3.0, id = "3").first
        s = SessionCore.tick(s, entry = 1, set = 1, w = 20.0, r = 12.0, at = 4.0, id = "4").first
        n = SessionCore.next(s, entry = 1, set = 1)
        assertEquals("the superset done: the next exercise", 2, n.entry)
        s = SessionCore.tick(s, entry = 2, set = 0, w = 8.0, r = 15.0, at = 5.0, id = "5").first
        assertEquals("all done: the Finish page", 3, SessionCore.next(s, entry = 2, set = 0).entry)
    }

    @Test fun aSkippedSetIsPassedOverNotDone() {
        var s = state(listOf(entry("bench", "Bench", sets = listOf(row(60.0, 8.0), row(60.0, 8.0), row(60.0, 8.0)))))
        s = SessionCore.skip(s, entry = 0, set = 0)
        assertEquals(1, SessionCore.setIndex(s, 0))
        assertFalse(s.workout!!.entries[0].sets[0].done)
        // picked by hand, it is back
        assertEquals(0, SessionCore.setIndex(s, 0, picked = 0))
        // a new workout forgets it
        val other = ActiveWorkout(id = "w2", name = "Other", unit = "kg", cur = 0, restSec = 90.0, rest = null, entries = s.workout!!.entries)
        val s2 = SessionCore.applySnapshot(s, snapOf(other))
        assertNull(s2.skipped)
        assertEquals(0, SessionCore.summary(s.workout!!, end = 1.0).sets)
    }

    @Test fun cardioIsTickedWithoutNumbers() {
        val s0 = state(listOf(entry("run", "Treadmill", mode = "cardio", sets = listOf(row(null, null)))))
        val (s, ev) = SessionCore.tick(s0, entry = 0, set = 0, w = null, r = null, at = 1.0, id = "c")
        assertTrue(s.workout!!.entries[0].sets[0].done)
        assertNull(ev!!["w"])
        assertNull(ev["r"])
    }

    @Test fun theSummaryCountsTimeVolumeAndRecords() {
        val bench = entry("bench", "Bench", sets = listOf(row(40.0, 10.0, warmup = true), row(80.0, 5.0), row(80.0, 6.0))).copy(best = BestSet(80.0, 5.0, null))
        val row1 = entry("row", "Row", sets = listOf(row(60.0, 8.0))).copy(best = BestSet(70.0, 8.0, null))
        val assist = entry("dip", "Assisted dip", sets = listOf(row(20.0, 8.0))).copy(best = BestSet(25.0, 8.0, null), assisted = true)
        val plank = entry("plank", "Plank", mode = "time", sets = listOf(row(null, null, sec = 60.0))).copy(best = BestSet(null, null, 50.0))
        val fresh = entry("new", "New thing", sets = listOf(row(100.0, 1.0)))
        var s = state(listOf(bench, row1, assist, plank, fresh), start = 1_000.0)
        for ((ei, si) in listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 2 to 0, 3 to 0, 4 to 0)) {
            val r = s.workout!!.entries[ei].sets[si]
            s = SessionCore.tick(s, entry = ei, set = si, w = r.w, r = r.r, sec = r.sec, at = 2.0, id = "$ei$si").first
        }
        val sum = SessionCore.summary(s.workout!!, end = 1_000.0 + 45 * 60_000)
        assertEquals(45.0 * 60, sum.duration!!, 0.0)
        assertEquals("warm-ups are not counted", 6, sum.sets)
        assertEquals(400.0 + 480 + 480 + 160 + 100, sum.volume, 0.0)   // 80×5, 80×6, 60×8, 20×8, 100×1
        assertEquals("more reps at the best weight, lighter assistance, a longer hold; no history is no PR",
            listOf("Bench", "Assisted dip", "Plank"), sum.prs)
        assertFalse(sum.endedOnPhone)
    }

    @Test fun anOlderPhoneWithoutTheNewFieldsStillReads() {
        val json = """{"v":1,"active":{"id":"w","name":"A","unit":"kg","cur":0,"restSec":90,"entries":[{"id":"x","name":"X","mode":"reps","sets":[{"w":1,"r":2,"done":false,"warmup":false}]}]}}"""
        val snap = Snapshot.parse(json)!!
        assertNull(snap.active?.start)
        assertNull(snap.active?.entries?.get(0)?.group)
        assertNull(snap.active?.entries?.get(0)?.sets?.get(0)?.sides)
        val newer = """{"v":1,"active":{"id":"w","name":"A","unit":"kg","cur":0,"restSec":90,"start":5,"entries":[{"id":"x","name":"X","mode":"reps","perSide":true,"group":"g","best":{"w":10,"r":5},"sets":[{"w":1,"r":2,"done":false,"warmup":false,"sides":{"L":{"w":1,"r":1,"done":true},"R":{"w":1,"r":1,"done":false}}}]}]}}"""
        val snap2 = Snapshot.parse(newer)!!
        assertEquals("R", snap2.active!!.entries[0].sets[0].sideUp)
        assertEquals(5.0, snap2.active!!.start!!, 0.0)
    }

    @Test fun aStateSavedByTheLastBuildStillLoads() {
        val saved = """{"settings":{},"unit":"kg","restSec":90,"finishedHere":[],"pending":{"x#0":{"w":1,"r":2,"at":3}},"entryIndex":0}"""
        val s = WatchJson.decodeFromString(SessionState.serializer(), saved)
        assertNull(s.skipped)
        assertNull(s.pending["x#0"]?.sec)
    }
}

/** The weigh-in, the app's language and the display choices the phone sends. */
class SettingsAndWeighInTest {
    @Test fun theWeighInGoesOutAndRidesOnTheStart() {
        val ev = SessionCore.weighIn(81.44, at = 5.0, id = "b")
        assertEquals("weighIn", ev!!["type"]!!.jsonPrimitive.content)
        assertEquals(81.4, ev["w"]!!.jsonPrimitive.doubleOrNull!!, 0.0)
        assertNull(SessionCore.weighIn(0.0, at = 5.0, id = "x"))
        val ready = ReadySession(routineIds = listOf("r"), name = "Push", entries = emptyList())
        val (_, start) = SessionCore.start(SessionState(), ready, wid = "watch-1", at = 5.0, id = "s", bw = 81.44)
        assertEquals(81.4, start["bw"]!!.jsonPrimitive.doubleOrNull!!, 0.0)
        val (_, noBw) = SessionCore.start(SessionState(), ready, wid = "watch-2", at = 5.0, id = "s2")
        assertNull(noBw["bw"])
    }

    @Test fun theSettingsReadWithAndWithoutTheNewFields() {
        val json = """{"v":1,"settings":{"health":true,"lang":"pt-BR","weighIn":false,"lastBw":80.5,"show":{"clip":false,"elapsed":true,"rest":"plain"}},"active":{"id":"w","name":"A","unit":"kg","cur":0,"restSec":90,"entries":[{"id":"x","name":"X","mode":"reps","step":1.25,"sets":[]}]}}"""
        val s = SessionCore.applySnapshot(SessionState(), Snapshot.parse(json)!!)
        assertEquals("pt-BR", s.settings.lang)
        assertEquals(false, s.settings.weighIn)
        assertEquals(80.5, s.settings.lastBw!!, 0.0)
        val show = s.settings.show ?: WatchShow()
        assertFalse(show.showClip)
        assertTrue(show.showHr)
        assertTrue(show.showElapsed)
        assertFalse(show.restRing)
        assertEquals(1.25, s.workout!!.entries[0].step!!, 0.0)
        val old = Snapshot.parse("""{"v":1,"settings":{"health":false}}""")!!
        val d = old.settings?.show ?: WatchShow()
        assertTrue(d.showClip && d.showHr && d.showLast && d.showNext && d.restRing)
        assertFalse(d.showElapsed || d.showKcal)
        assertNull(old.settings?.lang)
    }

    @Test fun aSnapshotWithTheAppleHealthListStillReads() {
        assertEquals(listOf("opengym-w-x"), Snapshot.parse("""{"v":1,"healthGone":["opengym-w-x"]}""")!!.healthGone)
    }
}
