package com.cezar.opengym.watchcore

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * What the phone sends the watch (frontend lib/watch-sync.js buildSnapshot), as the watch reads
 * it. A port of upstream's ios/App/WatchCore/Sources/WatchCore/Model.swift: plain data, no media.
 * Every field the phone may leave out is optional, so an older or a newer phone never makes the
 * watch fail to read a snapshot.
 */

/** The JSON the phone and the watch speak: unknown fields ignored, absent ones left at their defaults. */
val WatchJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** The snapshot version this watch reads (lib/watch-sync.js SNAPSHOT_VERSION). */
const val SNAPSHOT_VERSION = 1

@Serializable
data class Snapshot(
    val v: Int,
    val unit: String? = null,
    val restSec: Double? = null,
    val active: ActiveWorkout? = null,
    val ready: Ready? = null,
    val settings: WatchSettings? = null,
    /** Apple Health ids of deleted workouts: the Android watch has no copies of its own, read and ignored. */
    val healthGone: List<String>? = null,
) {
    val compatible: Boolean get() = v == SNAPSHOT_VERSION

    companion object {
        /** null for JSON that is not a snapshot at all. */
        fun parse(json: String): Snapshot? = try { WatchJson.decodeFromString(serializer(), json) } catch (e: Exception) { null }
    }
}

/** What the watch can start by itself: today's planned session and a few other routines. */
@Serializable
data class Ready(
    val today: ReadySession? = null,
    val others: List<ReadySession> = emptyList(),
)

@Serializable
data class ReadySession(
    val routineIds: List<String>,
    val name: String,
    val entries: List<Entry>,
)

@Serializable
data class ActiveWorkout(
    val id: String? = null,
    val name: String,
    val unit: String,
    val cur: Int,
    val restSec: Double,
    val rest: RestInfo? = null,
    val entries: List<Entry>,
    /** When it started (ms since 1970), for the summary's duration. */
    val start: Double? = null,
)

@Serializable
data class RestInfo(val endsAt: Double)

@Serializable
data class Entry(
    val id: String,
    val name: String,
    /** "reps", "time" (a hold, `sec` per set) or "cardio" (logged on the phone). */
    val mode: String,
    val restSec: Double? = null,
    val last: String? = null,
    val sets: List<SetRow>,
    /** No load of its own: `w` is added weight, shown only when there is some. */
    val bodyweight: Boolean? = null,
    /** Unilateral: each set is done one side at a time (`sides`). */
    val perSide: Boolean? = null,
    /** A superset: neighbours with the same group are done in turns, the rest after each round. */
    val group: String? = null,
    /** The best set before this workout, for the PRs at the finish. */
    val best: BestSet? = null,
    /** Assisted (a lighter weight is the better one). */
    val assisted: Boolean? = null,
    /** The weight step this exercise moves in on the phone (its plates or machine stack). */
    val step: Double? = null,
) {
    val isTimed: Boolean get() = mode == "time"
    val isCardio: Boolean get() = mode == "cardio"
    val isBodyweight: Boolean get() = bodyweight == true
}

@Serializable
data class BestSet(
    val w: Double? = null,
    val r: Double? = null,
    val sec: Double? = null,
)

/** One side of a per-side set. */
@Serializable
data class SideRow(
    val w: Double? = null,
    val r: Double? = null,
    val done: Boolean = false,
)

@Serializable
data class Sides(
    val L: SideRow,
    val R: SideRow,
) {
    operator fun get(side: String): SideRow = if (side == "R") R else L
    fun with(side: String, row: SideRow): Sides = if (side == "R") copy(R = row) else copy(L = row)
}

@Serializable
data class SetRow(
    val w: Double? = null,
    val r: Double? = null,
    val sec: Double? = null,
    val done: Boolean = false,
    val warmup: Boolean = false,
    /** When it was ticked (ms since 1970), on whichever side ticked it: the later tick wins. */
    val at: Double? = null,
    /** A per-side set's two sides; `done` is both of them. */
    val sides: Sides? = null,
) {
    /** The side up next on a per-side set ("L", then "R"), or null. */
    val sideUp: String?
        get() {
            val s = sides ?: return null
            if (!s.L.done) return "L"
            if (!s.R.done) return "R"
            return null
        }
}

/** The phone's Settings → watch (frontend lib/watch-settings.js). */
@Serializable
data class WatchSettings(
    /** Health session on the watch (Apple Health upstream; Health Services here, P2). */
    val health: Boolean? = null,
    /** The wrist taps when a rest is over. */
    val restHaptics: Boolean? = null,
    /** What the bezel turns first on a set: "reps" or "weight". */
    val crown: String? = null,
    /** The app's language ("en", "pt", …): the watch shows its words in it. */
    val lang: String? = null,
    /** Whether a workout started here asks for the body weight first (the phone's check-in). */
    val weighIn: Boolean? = null,
    /** The last body weight logged, where the weigh-in starts. */
    val lastBw: Double? = null,
    /** What the watch shows during a workout; null is the default. */
    val show: WatchShow? = null,
)

/** What a workout screen shows. Each null means its default. */
@Serializable
data class WatchShow(
    val clip: Boolean? = null,
    val hr: Boolean? = null,
    val last: Boolean? = null,
    val elapsed: Boolean? = null,
    val kcal: Boolean? = null,
    val next: Boolean? = null,
    /** "ring" (default) or "plain" (the countdown alone). */
    val rest: String? = null,
) {
    val showClip: Boolean get() = clip ?: true
    val showHr: Boolean get() = hr ?: true
    val showLast: Boolean get() = last ?: true
    val showElapsed: Boolean get() = elapsed ?: false
    val showKcal: Boolean get() = kcal ?: false
    val showNext: Boolean get() = next ?: true
    val restRing: Boolean get() = rest != "plain"
}
