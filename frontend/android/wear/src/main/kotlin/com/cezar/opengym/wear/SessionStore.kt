package com.cezar.opengym.wear

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cezar.opengym.watchcore.Event
import com.cezar.opengym.watchcore.SessionCore
import com.cezar.opengym.watchcore.SessionState
import com.cezar.opengym.watchcore.Snapshot
import com.cezar.opengym.watchcore.WorkoutSummary
import com.cezar.opengym.wear.data.Rest
import com.cezar.opengym.wear.data.RestClock
import com.cezar.opengym.wear.data.StateFile
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.UUID

/**
 * The watch's session, as upstream's SessionStore.swift keeps it: one SessionState, changed only
 * through SessionCore, saved to a file after every change (a killed app comes back where it was),
 * and each event it returns sent to the phone as its own DataItem (/opengym/v1/event/<id>), which
 * Play services delivers when it can and the phone deletes once applied (design §3).
 *
 * Beside it, the watch's own rest (RestClock, its alarm) and the summary at a workout's end.
 */
object SessionStore {
    const val TAG = "OpenGymWear"
    const val SNAPSHOT = "/opengym/v1/snapshot"
    const val EVENT_PREFIX = "/opengym/v1/event/"

    private val lock = Any()
    private var files: Pair<StateFile<SessionState>, StateFile<Rest>>? = null
    private val flow = MutableStateFlow<SessionState?>(null)
    private val restFlow = MutableStateFlow<Rest?>(null)

    /** The phone sent a snapshot this watch cannot read (another protocol version). */
    val incompatible = MutableStateFlow(false)
    /** The workout that just ended (here, or on the phone), for the summary page; null once seen. */
    val summary = MutableStateFlow<WorkoutSummary?>(null)

    private fun files(ctx: Context) = files ?: Pair(
        StateFile(File(ctx.filesDir, "session.json"), SessionState.serializer()) { SessionState() },
        StateFile(File(ctx.filesDir, "rest.json"), Rest.serializer()) { Rest(0, 0) },
    ).also { files = it }

    private fun load(ctx: Context) {
        if (flow.value != null) return
        synchronized(lock) {
            if (flow.value != null) return
            val (s, r) = files(ctx)
            restFlow.value = r.read().takeIf { RestClock.running(it, now()) }
            flow.value = s.read()
        }
    }

    fun state(ctx: Context): StateFlow<SessionState?> { load(ctx); return flow }
    fun rest(ctx: Context): StateFlow<Rest?> { load(ctx); return restFlow }

    private fun now() = System.currentTimeMillis()

    /** Every change: through here, saved, then shown. */
    private fun commit(ctx: Context, change: (SessionState) -> SessionState) {
        load(ctx)
        synchronized(lock) {
            val next = change(flow.value ?: SessionState())
            files(ctx).first.write(next)
            flow.value = next
        }
    }

    private fun setRest(ctx: Context, r: Rest?) {
        synchronized(lock) {
            files(ctx).second.write(r ?: Rest(0, 0))
            restFlow.value = r
        }
        RestAlarm.set(ctx, r?.endsAt)
    }

    fun applySnapshotJson(ctx: Context, json: String) {
        val snap = Snapshot.parse(json) ?: return
        if (!snap.compatible) { incompatible.value = true; return }
        incompatible.value = false
        var before: SessionState? = null
        var after: SessionState? = null
        commit(ctx) { s -> before = s; SessionCore.applySnapshot(s, snap).also { after = it } }
        val gone = before?.workout
        if (gone != null && after?.workout == null && gone.id !in (after?.finishedHere ?: emptyList())) {
            // Finished or discarded on the phone while it ran here.
            summary.value = SessionCore.summary(gone, end = now().toDouble(), endedOnPhone = true)
            setRest(ctx, null)
        }
        if (after?.workout != null) {
            val phoneRest = after?.workout?.rest?.endsAt?.toLong()
            val followed = RestClock.follow(restFlow.value, phoneRest, now())
            if (followed !== restFlow.value) setRest(ctx, followed)
        }
    }

    /** The phone's current snapshot, read straight from the Data Layer (at launch). */
    fun refreshFromPhone(ctx: Context) {
        Wearable.getDataClient(ctx).getDataItems(Uri.parse("wear://*$SNAPSHOT")).addOnSuccessListener { items ->
            try {
                items.maxByOrNull { DataMapItem.fromDataItem(it).dataMap.getLong("t") }?.let {
                    DataMapItem.fromDataItem(it).dataMap.getString("json")?.let { j -> applySnapshotJson(ctx, j) }
                }
            } finally { items.release() }
        }
    }

    fun newId(): String = "wear-" + UUID.randomUUID().toString()

    /** A set done here, with the numbers it was done with: the next one comes up, a rest if due. */
    fun tick(ctx: Context, entry: Int, set: Int, side: String? = null, w: Double?, r: Double?, sec: Double? = null) {
        var out: Event? = null
        var restSec: Double? = null
        commit(ctx) { s ->
            val (next, event) = SessionCore.tick(s, entry, set, side, w, r, sec, at = now().toDouble(), id = newId())
            out = event
            if (event == null) return@commit next
            val n = SessionCore.next(next, entry, set)
            val wk = next.workout!!
            if (n.rest && n.entry < wk.entries.size) {
                restSec = wk.entries[entry].restSec?.takeIf { it > 0 } ?: wk.restSec.takeIf { it > 0 } ?: next.restSec
            }
            next.copy(entryIndex = n.entry)
        }
        out?.let { send(ctx, it) }
        restSec?.let { setRest(ctx, RestClock.start(now(), it)) }
    }

    fun skip(ctx: Context, entry: Int, set: Int) = commit(ctx) { SessionCore.skip(it, entry, set) }

    fun goTo(ctx: Context, entry: Int) = commit(ctx) { it.copy(entryIndex = entry) }

    fun adjustRest(ctx: Context, deltaSec: Int) = setRest(ctx, RestClock.adjust(restFlow.value, now(), deltaSec))

    fun skipRest(ctx: Context) = setRest(ctx, null)

    /** The alarm fired: the rest is over. */
    fun restOver(ctx: Context) { if (!RestClock.running(restFlow.value, now())) setRest(ctx, null) }

    fun finish(ctx: Context) {
        var out: Event? = null
        val w = state(ctx).value?.workout ?: return
        commit(ctx) { s -> SessionCore.finish(s, at = now().toDouble(), id = newId()).also { out = it.second }.first }
        summary.value = SessionCore.summary(w, end = now().toDouble())
        setRest(ctx, null)
        out?.let { send(ctx, it) }
    }

    /** One event, one DataItem: delivered in order when the phone is there, kept until it is. */
    private fun send(ctx: Context, event: Event) {
        val id = (event["id"] as? JsonPrimitive)?.content ?: return
        val req = PutDataMapRequest.create(EVENT_PREFIX + id).apply {
            dataMap.putString("json", event.toString())
            dataMap.putLong("t", now())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(ctx).putDataItem(req)
            .addOnSuccessListener { Log.i(TAG, "watch: event $id queued") }
            .addOnFailureListener { Log.w(TAG, "watch: event $id not queued", it) }
    }

    /** How many events the phone has not taken yet (their DataItems still there). */
    fun pendingEvents(ctx: Context, done: (Int) -> Unit) {
        Wearable.getDataClient(ctx).getDataItems(Uri.parse("wear://*$EVENT_PREFIX"), DataClient.FILTER_PREFIX)
            .addOnSuccessListener { items -> try { done(items.count) } finally { items.release() } }
    }

    /** Whether the phone is connected now (Bluetooth or Wi-Fi), for the "no connection" mark. */
    fun phoneConnected(ctx: Context, done: (Boolean) -> Unit) {
        Wearable.getNodeClient(ctx).connectedNodes
            .addOnSuccessListener { done(it.isNotEmpty()) }
            .addOnFailureListener { done(false) }
    }
}
