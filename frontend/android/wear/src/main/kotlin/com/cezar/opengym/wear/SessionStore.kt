package com.cezar.opengym.wear

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cezar.opengym.watchcore.Event
import com.cezar.opengym.watchcore.SessionCore
import com.cezar.opengym.watchcore.SessionState
import com.cezar.opengym.watchcore.Snapshot
import com.cezar.opengym.watchcore.WatchJson
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.UUID

/**
 * The watch's session, as upstream's SessionStore.swift keeps it: one SessionState, changed only
 * through SessionCore, saved to a file after every change (so a killed app comes back where it
 * was), and each event it returns sent to the phone as its own DataItem
 * (/opengym/v1/event/<id>), which Play services delivers when it can and the phone deletes once
 * applied (specs/fork-watch design §3).
 */
object SessionStore {
    const val TAG = "OpenGymWear"
    const val SNAPSHOT = "/opengym/v1/snapshot"
    const val EVENT_PREFIX = "/opengym/v1/event/"

    private const val FILE = "session.json"
    private val lock = Any()
    private val flow = MutableStateFlow<SessionState?>(null)
    /** The phone sent a snapshot this watch cannot read (another protocol version). */
    val incompatible = MutableStateFlow(false)

    fun state(ctx: Context): StateFlow<SessionState?> {
        if (flow.value == null) synchronized(lock) { if (flow.value == null) flow.value = load(ctx) }
        return flow
    }

    private fun current(ctx: Context): SessionState = state(ctx).value ?: SessionState()

    private fun load(ctx: Context): SessionState = try {
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) WatchJson.decodeFromString(SessionState.serializer(), f.readText()) else SessionState()
    } catch (e: Exception) {
        Log.w(TAG, "session not read, starting clean", e)
        SessionState()
    }

    private fun save(ctx: Context, s: SessionState) {
        val f = File(ctx.filesDir, FILE)
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(WatchJson.encodeToString(SessionState.serializer(), s))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    /** Every change: through here, saved, then shown. */
    private fun commit(ctx: Context, change: (SessionState) -> SessionState) {
        synchronized(lock) {
            val next = change(current(ctx))
            save(ctx, next)
            flow.value = next
        }
    }

    fun applySnapshotJson(ctx: Context, json: String) {
        val snap = Snapshot.parse(json) ?: return
        if (!snap.compatible) { incompatible.value = true; return }
        incompatible.value = false
        commit(ctx) { SessionCore.applySnapshot(it, snap) }
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

    /** A set done here, with the numbers it was done with. Returns the state after it. */
    fun tick(ctx: Context, entry: Int, set: Int, side: String? = null, w: Double?, r: Double?, sec: Double? = null) {
        var out: Event? = null
        commit(ctx) { s ->
            val (next, event) = SessionCore.tick(s, entry, set, side, w, r, sec, at = System.currentTimeMillis().toDouble(), id = newId())
            out = event
            if (event == null) next else next.copy(entryIndex = SessionCore.next(next, entry, set).entry)
        }
        out?.let { send(ctx, it) }
    }

    fun finish(ctx: Context) {
        var out: Event? = null
        commit(ctx) { s -> SessionCore.finish(s, at = System.currentTimeMillis().toDouble(), id = newId()).also { out = it.second }.first }
        out?.let { send(ctx, it) }
    }

    fun goTo(ctx: Context, entry: Int) = commit(ctx) { it.copy(entryIndex = entry) }

    /** One event, one DataItem: delivered in order when the phone is there, kept until it is. */
    private fun send(ctx: Context, event: JsonObject) {
        val id = (event["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return
        val req = PutDataMapRequest.create(EVENT_PREFIX + id).apply {
            dataMap.putString("json", event.toString())
            dataMap.putLong("t", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(ctx).putDataItem(req)
            .addOnSuccessListener { Log.i(TAG, "watch: event $id queued") }
            .addOnFailureListener { Log.w(TAG, "watch: event $id not queued", it) }
    }

    /** Events the phone has not taken yet (their DataItems still there). */
    fun pendingEvents(ctx: Context, done: (Int) -> Unit) {
        Wearable.getDataClient(ctx).getDataItems(Uri.parse("wear://*$EVENT_PREFIX"), DataClient.FILTER_PREFIX)
            .addOnSuccessListener { items -> try { done(items.count) } finally { items.release() } }
    }
}
