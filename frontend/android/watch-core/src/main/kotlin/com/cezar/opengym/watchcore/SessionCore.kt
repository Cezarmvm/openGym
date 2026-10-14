package com.cezar.opengym.watchcore

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * The session as the watch keeps it, and every change to it, as pure functions: the watch app's
 * SessionStore only calls these, saves the state after each, and sends the events they return.
 * A port of upstream's ios/App/WatchCore/Sources/WatchCore/SessionCore.swift, function for
 * function, so the Wear OS watch behaves as the Apple Watch does; the cases below are upstream's.
 * Left out: the Apple Health deletions (healthToDelete, healthDeleted*), which have no Wear OS side.
 *
 *   watch app never opened before the workout started on the phone
 *       the phone keeps its snapshot as a DataItem, which the watch reads when it launches
 *       (applySnapshot): it picks the running workout up where the phone has it.
 *   watch app killed and relaunched mid-workout
 *       the whole state is saved after every change and restored at launch; then the latest
 *       snapshot is applied over it, with the watch's own unconfirmed ticks kept (below).
 *   phone asleep, locked in a pocket, app killed, Bluetooth off, out of range
 *       the watch runs the session alone; each tick, start and finish is an event sent as a
 *       DataItem, which Play services delivers, in order, when it can; the phone applies each
 *       one once, by its id.
 *   a snapshot older than the watch's own ticks (sent before the phone applied them)
 *       a tick stays pending until a snapshot shows that set done at the tick's time or later;
 *       until then the snapshot's copy of that set is replaced by the watch's.
 *   the same set changed on both sides
 *       the later tick (`at`) wins, on both sides: here in applySnapshot, on the phone in the
 *       workout screen's watch handler.
 *   finished on the watch while the phone was away
 *       the workout leaves the watch at once; a snapshot that still shows it running (the phone
 *       has not heard yet) does not bring it back.
 *   finished on the phone while the watch was away
 *       a snapshot without it ends it here; ticks the watch made meanwhile are still sent, and
 *       the phone writes them into the saved workout.
 *   started on the watch while the phone was away
 *       kept until the phone reports it back running; a snapshot without it does not end it.
 */

@Serializable
data class PendingTick(
    val w: Double? = null,
    val r: Double? = null,
    val at: Double,
    /** A hold's seconds. */
    val sec: Double? = null,
)

@Serializable
data class SessionState(
    val workout: ActiveWorkout? = null,
    val ready: Ready? = null,
    val settings: WatchSettings = WatchSettings(),
    val unit: String = "kg",
    val restSec: Double = 90.0,
    /** A session started here that the phone has not reported back running yet. */
    val startedHere: String? = null,
    /** Sessions finished here that the phone may still report running for a while. */
    val finishedHere: List<String> = emptyList(),
    /** Ticks made here, by "exerciseId#setIndex", until a snapshot shows them. */
    val pending: Map<String, PendingTick> = emptyMap(),
    /** The exercise on screen. */
    val entryIndex: Int = 0,
    /** Sets passed over here ("exerciseId#setIndex"), not done: the next one comes up instead. */
    val skipped: List<String>? = null,
)

/** An event for the phone (lib/watch-sync.js applyEvents), as the JSON it is sent as. */
typealias Event = JsonObject

/** Where to go after a set, and whether a rest comes first. */
data class Next(val entry: Int, val rest: Boolean)

/** Sets done and sets in all. */
data class Progress(val done: Int, val total: Int)

/** The end of a workout, on one screen: how long, how much, and what was a first. */
@Serializable
data class WorkoutSummary(
    val name: String,
    val unit: String,
    /** Seconds. */
    val duration: Double? = null,
    /** Work sets done (a per-side set once both sides are). */
    val sets: Int,
    val volume: Double,
    /** The exercises with a personal record in this workout. */
    val prs: List<String>,
    val hrAvg: Double? = null,
    val hrMax: Double? = null,
    val calories: Double? = null,
    /** The phone ended it (finished or discarded there) while the watch had it running. */
    val endedOnPhone: Boolean,
)

private fun ev(vararg pairs: Pair<String, Any?>): Event {
    val m = LinkedHashMap<String, JsonElement>()
    for ((k, v) in pairs) {
        when (v) {
            null -> {}
            is JsonElement -> m[k] = v
            is String -> m[k] = JsonPrimitive(v)
            // Whole numbers (timestamps, indexes, reps) go out as integers, as the phone writes them.
            is Double -> m[k] = if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)
            is Number -> m[k] = JsonPrimitive(v)
            is Boolean -> m[k] = JsonPrimitive(v)
            is List<*> -> m[k] = JsonArray(v.map { JsonPrimitive(it as String) })
            else -> error("unsupported event value for $k")
        }
    }
    return JsonObject(m)
}

/** Rounded to one decimal, as the phone keeps body weights. */
private fun oneDecimal(x: Double): Double = (x * 10).roundToLong() / 10.0

private fun <T> List<T>.replaced(i: Int, value: T): List<T> = toMutableList().also { it[i] = value }

object SessionCore {
    fun key(exId: String, si: Int, side: String? = null): String =
        if (side != null) "$exId#$si#$side" else "$exId#$si"

    /** A snapshot from the phone, over what the watch has. */
    fun applySnapshot(s0: SessionState, snap: Snapshot): SessionState {
        var s = s0.copy(ready = snap.ready)
        snap.unit?.let { s = s.copy(unit = it) }
        snap.restSec?.let { if (it > 0) s = s.copy(restSec = it) }
        snap.settings?.let { s = s.copy(settings = it) }

        // A session started here that the phone has not reported back yet stays, whatever it sends.
        val unconfirmed = s.workout?.id != null && s.workout?.id == s.startedHere
        val incoming = snap.active
        if (incoming == null || (incoming.id != null && s.finishedHere.contains(incoming.id))) {
            // No workout on the phone, or one the watch has finished already.
            if (unconfirmed) return s
            return s.copy(workout = null, pending = emptyMap())
        }
        var next = incoming
        val same = next.id != null && next.id == s.workout?.id
        if (next.id != null && next.id == s.startedHere) s = s.copy(startedHere = null)
        if (!same && unconfirmed) {
            // The phone runs a different workout than the one started here and not yet confirmed:
            // the watch keeps its own; the phone keeps its own (frontend: a start while another runs).
            return s
        }
        if (!same) {
            // Another workout (or the first one this watch sees): its own position, no old ticks.
            s = s.copy(pending = emptyMap(), skipped = null, entryIndex = next.cur)
        }
        // The watch's ticks the phone has not shown yet win; the later tick wins on a set both changed.
        val pending = s.pending.toMutableMap()
        val entries = next.entries.toMutableList()
        for ((ei, e) in next.entries.withIndex()) {
            val sets = e.sets.toMutableList()
            for ((si, row) in e.sets.withIndex()) {
                val k = key(e.id, si)
                val p = pending[k]
                if (p != null) {
                    if (row.done && (row.at ?: Double.POSITIVE_INFINITY) >= p.at) {
                        pending.remove(k)          // the phone has it (or a later change of its own)
                    } else {
                        sets[si] = sets[si].copy(
                            done = true,
                            w = p.w ?: row.w,
                            r = p.r ?: row.r,
                            sec = p.sec ?: sets[si].sec,
                            at = p.at,
                        )
                    }
                }
                // A per-side set: each side by itself, the same way.
                for (side in listOf("L", "R")) {
                    val ks = key(e.id, si, side)
                    val ps = pending[ks] ?: continue
                    val have = sets[si]
                    if (have.sides?.get(side)?.done == true && (row.at ?: Double.POSITIVE_INFINITY) >= ps.at) {
                        pending.remove(ks)
                    } else if (have.sides != null) {
                        sets[si] = markSide(have, side, ps.w, ps.r, ps.at)
                    } else {
                        pending.remove(ks)         // no longer a per-side set on the phone
                    }
                }
            }
            entries[ei] = e.copy(sets = sets)
        }
        next = next.copy(entries = entries)
        return s.copy(
            pending = pending,
            entryIndex = min(max(0, s.entryIndex), next.entries.size),   // the last page is Finish
            workout = next,
        )
    }

    /**
     * One side of a per-side set done, the row's own numbers following both sides (as the phone's
     * syncSideAggregate: reps the sum, weight the heavier, done when both are).
     */
    fun markSide(row0: SetRow, side: String, w: Double?, r: Double?, at: Double): SetRow {
        val sides0 = row0.sides ?: return row0
        var one = sides0[side].copy(done = true)
        if (w != null) one = one.copy(w = w)
        if (r != null) one = one.copy(r = r)
        val sides = sides0.with(side, one)
        return row0.copy(
            sides = sides,
            done = sides.L.done && sides.R.done,
            r = (sides.L.r ?: 0.0) + (sides.R.r ?: 0.0),
            w = max(sides.L.w ?: 0.0, sides.R.w ?: 0.0),
            at = max(row0.at ?: 0.0, at),
        )
    }

    /**
     * A set ticked here: the state with it done, and the event for the phone. `side` ticks one
     * side of a per-side set; `sec` is a hold's seconds; a cardio tick carries no numbers.
     */
    fun tick(
        s0: SessionState, entry: Int, set: Int, side: String? = null, w: Double?, r: Double?, sec: Double? = null,
        at: Double, id: String, hr: JsonObject? = null,
    ): Pair<SessionState, Event?> {
        val workout = s0.workout ?: return s0 to null
        if (entry !in workout.entries.indices || set !in workout.entries[entry].sets.indices) return s0 to null
        val e = workout.entries[entry]
        val oneSide = side?.takeIf { e.sets[set].sides != null }
        val row = if (oneSide != null) {
            markSide(e.sets[set], oneSide, w, r, at)
        } else {
            var x = e.sets[set].copy(done = true, at = at)
            if (w != null) x = x.copy(w = w)
            if (r != null) x = x.copy(r = r)
            if (sec != null) x = x.copy(sec = sec)
            x
        }
        val updated = workout.copy(entries = workout.entries.replaced(entry, e.copy(sets = e.sets.replaced(set, row))))
        val s = s0.copy(
            workout = updated,
            pending = s0.pending + (key(e.id, set, oneSide) to PendingTick(w = w, r = r, at = at, sec = sec)),
        )
        val event = ev(
            "id" to id, "type" to "setDone", "wid" to (workout.id ?: ""), "exId" to e.id,
            "entryIdx" to entry, "setIdx" to set, "at" to at,
            "w" to w, "r" to r, "sec" to sec, "side" to oneSide, "hr" to hr,
        )
        return s to event
    }

    /** A set passed over here: not done, the next one comes up (a tap on its dot brings it back). */
    fun skip(s0: SessionState, entry: Int, set: Int): SessionState {
        val w = s0.workout ?: return s0
        if (entry !in w.entries.indices || set !in w.entries[entry].sets.indices) return s0
        val k = key(w.entries[entry].id, set)
        return s0.copy(skipped = ((s0.skipped ?: emptyList()) + k).toSortedSet().toList())
    }

    fun isSkipped(s: SessionState, exId: String, si: Int): Boolean = s.skipped?.contains(key(exId, si)) ?: false

    /**
     * Where to go after a set of exercise `ei` (set `si`) was done, and whether a rest comes first.
     *   per-side set with a side to go   the same exercise, no rest (the other side now)
     *   superset                          the next exercise of the group in turn; the rest only
     *                                     after the last one of a round
     *   otherwise                         this exercise while it has sets to do, then the next one
     *                                     that has; after the last, the Finish page (index = count)
     */
    fun next(after: SessionState, entry: Int, set: Int): Next {
        val s = after
        val w = s.workout ?: return Next(s.entryIndex, false)
        if (entry !in w.entries.indices) return Next(s.entryIndex, false)
        val ei = entry
        val count = w.entries.size
        if (set in w.entries[ei].sets.indices && w.entries[ei].sets[set].sideUp != null) return Next(ei, false)
        val open: (Int) -> Boolean = { setIndex(s, it) != null }
        val g = w.entries[ei].group
        if (g != null) {
            var lo = ei
            var hi = ei
            while (lo > 0 && w.entries[lo - 1].group == g) lo -= 1
            while (hi + 1 < count && w.entries[hi + 1].group == g) hi += 1
            if (hi > lo) {
                val members = (lo..hi).toList()
                val inTurn = members.filter { it > ei } + members.filter { it <= ei }
                inTurn.firstOrNull(open)?.let { return Next(it, it <= ei) }
                ((hi + 1 until count).firstOrNull(open) ?: (0 until lo).firstOrNull(open))?.let { return Next(it, true) }
                return Next(count, true)
            }
        }
        if (open(ei)) return Next(ei, true)
        ((ei + 1 until count).firstOrNull(open) ?: (0 until ei).firstOrNull(open))?.let { return Next(it, true) }
        return Next(count, true)
    }

    /** The weigh-in before a workout started here, for the phone's body weight list. */
    fun weighIn(w: Double, at: Double, id: String): Event? {
        if (!(w > 0 && w < 700)) return null
        return ev("id" to id, "type" to "weighIn", "w" to oneDecimal(w), "at" to at)
    }

    /** A ready session started here, with the body weight just weighed in (or none). */
    fun start(s0: SessionState, session: ReadySession, wid: String, at: Double, id: String, bw: Double? = null): Pair<SessionState, Event> {
        val s = s0.copy(
            workout = ActiveWorkout(id = wid, name = session.name, unit = s0.unit, cur = 0, restSec = s0.restSec, rest = null, entries = session.entries, start = at),
            startedHere = wid,
            pending = emptyMap(),
            skipped = null,
            entryIndex = 0,
        )
        val event = ev(
            "id" to id, "type" to "start", "routineIds" to session.routineIds, "wid" to wid, "at" to at,
            "bw" to bw?.takeIf { it > 0 }?.let(::oneDecimal),
        )
        return s to event
    }

    /** The session finished here. */
    fun finish(s0: SessionState, at: Double, id: String): Pair<SessionState, Event?> {
        val w = s0.workout ?: return s0 to null
        val finished = if (w.id != null) (s0.finishedHere + w.id).takeLast(5) else s0.finishedHere
        val s = s0.copy(workout = null, pending = emptyMap(), skipped = null, startedHere = null, finishedHere = finished)
        return s to ev("id" to id, "type" to "finish", "wid" to (w.id ?: ""), "at" to at)
    }

    /** The set to do next in an exercise: the one picked, or the first not done. */
    fun setIndex(s: SessionState, entry: Int, picked: Int? = null): Int? {
        val w = s.workout ?: return null
        if (entry !in w.entries.indices) return null
        val sets = w.entries[entry].sets
        if (picked != null && picked in sets.indices && !sets[picked].done) return picked
        val exId = w.entries[entry].id
        return sets.indices.firstOrNull { !sets[it].done && !isSkipped(s, exId, it) }
    }

    /** Sets done and sets in all, for the progress ring. */
    fun progress(s: SessionState): Progress {
        val w = s.workout ?: return Progress(0, 0)
        val all = w.entries.flatMap { it.sets }.filter { !it.warmup }
        return Progress(all.count { it.done }, all.size)
    }

    /** The workout as the summary shows it at its end (here, or on the phone). */
    fun summary(w: ActiveWorkout, end: Double, startedAt: Double? = null, endedOnPhone: Boolean = false): WorkoutSummary {
        var sets = 0
        var volume = 0.0
        val prs = mutableListOf<String>()
        for (e in w.entries) {
            val work = e.sets.filter { !it.warmup }
            for (row in work) {
                val sides = row.sides
                if (sides != null) {
                    for (one in listOf(sides.L, sides.R)) if (one.done) volume += (one.w ?: 0.0) * (one.r ?: 0.0)
                    if (row.done) sets += 1
                } else if (row.done) {
                    sets += 1
                    if (!e.isTimed && !e.isCardio) volume += (row.w ?: 0.0) * (row.r ?: 0.0)
                }
            }
            val best = e.best
            if (best != null && beatsBest(e, work.filter { it.done }, best)) prs += e.name
        }
        val from = w.start ?: startedAt
        return WorkoutSummary(
            name = w.name, unit = w.unit, duration = from?.let { max(0.0, (end - it) / 1000) },
            sets = sets, volume = volume, prs = prs, endedOnPhone = endedOnPhone,
        )
    }

    /**
     * Whether one of these done sets beats the best before this workout: a heavier weight (a
     * lighter one when assisted), or more reps at the same; a longer hold. No history, no PR.
     */
    fun beatsBest(e: Entry, done: List<SetRow>, best: BestSet): Boolean {
        if (e.isTimed) {
            val b = best.sec ?: return false
            if (b <= 0) return false
            return done.any { (it.sec ?: 0.0) > b }
        }
        if (e.isCardio) return false
        val bw = best.w ?: return false
        val br = best.r ?: return false
        if (!(bw > 0 || br > 0)) return false
        val assisted = e.assisted == true
        return done.any { row ->
            val w = row.w ?: 0.0
            val r = row.r ?: 0.0
            if (w != bw) w > 0 && (if (assisted) w < bw else w > bw) else r > br
        }
    }
}
