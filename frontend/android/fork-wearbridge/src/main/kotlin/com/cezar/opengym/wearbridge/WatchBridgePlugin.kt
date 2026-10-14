package com.cezar.opengym.wearbridge

import android.net.Uri
import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The phone's end of the Wear OS watch link, for upstream's lib/watch-sync.js. The same calls as
 * upstream's iOS WatchBridgePlugin.swift, over the Wearable Data Layer:
 *
 *   status()              { supported, activated, paired, installed, reachable }
 *   sendSnapshot({json})  { sent }  the snapshot as the DataItem /opengym/v1/snapshot (urgent)
 *   drain()               { events }  the inbox, in order (PhoneListenerService fills it)
 *   ack({ids})            out of the inbox, and their DataItems deleted: the watch's confirmation
 *   openOnWatch()         { ok }  a message: the watch shows its ongoing activity and taps once (D-6)
 *   sendClips(), dropClips()   nothing: the Wear OS watch shows no animations
 *   events: watchEvent (a new event is in the inbox), watchState (the watch app came or went)
 *
 * Play services calls are blocking here, on a single background thread, never on the page's.
 */
@CapacitorPlugin(name = "WatchBridge")
class WatchBridgePlugin : Plugin() {
    private val io = Executors.newSingleThreadExecutor()

    override fun load() {
        live = WeakReference(this)
    }

    override fun handleOnDestroy() {
        if (live?.get() === this) live = null
        io.shutdown()
    }

    private fun bg(call: PluginCall, body: () -> Unit) = io.execute {
        try { body() } catch (e: Exception) {
            Log.w(WearPaths.TAG, "WatchBridge.${call.methodName}", e)
            call.reject(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = Tasks.await(task, 10, TimeUnit.SECONDS)

    @PluginMethod
    fun status(call: PluginCall) = bg(call) {
        val ctx = context
        val connected = try { await(Wearable.getNodeClient(ctx).connectedNodes) } catch (e: Exception) { null }
        val withApp = try {
            await(Wearable.getCapabilityClient(ctx).getCapability(WearPaths.CAP_WATCH, CapabilityClient.FILTER_ALL)).nodes
        } catch (e: Exception) { null }
        val reachable = withApp.orEmpty().any { n -> connected.orEmpty().any { it.id == n.id } }
        call.resolve(JSObject().apply {
            put("supported", connected != null)
            put("activated", true)
            put("paired", connected.orEmpty().isNotEmpty() || withApp.orEmpty().isNotEmpty())
            put("installed", withApp.orEmpty().isNotEmpty())
            put("reachable", reachable)
        })
    }

    @PluginMethod
    fun sendSnapshot(call: PluginCall) {
        val json = call.getString("json") ?: return call.reject("json is required")
        bg(call) {
            val req = PutDataMapRequest.create(WearPaths.SNAPSHOT).apply {
                dataMap.putString("json", json)
                dataMap.putLong("t", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            val sent = try { await(Wearable.getDataClient(context).putDataItem(req)); true } catch (e: Exception) {
                Log.w(WearPaths.TAG, "snapshot not put", e)
                false
            }
            call.resolve(JSObject().put("sent", sent))
        }
    }

    @PluginMethod
    fun drain(call: PluginCall) = bg(call) {
        // Events the listener did not see (it was not running, or the app was being updated) are
        // still DataItems: picked up here, the inbox ignores the ones it has.
        try {
            val items = await(Wearable.getDataClient(context).getDataItems(Uri.parse("wear://*" + WearPaths.EVENT_PREFIX), com.google.android.gms.wearable.DataClient.FILTER_PREFIX))
            try { for (item in items) WearPaths.keep(context, item) } finally { items.release() }
        } catch (e: Exception) { Log.w(WearPaths.TAG, "event items not read", e) }
        val events = JSArray()
        for (it in WearPaths.inbox(context).all()) {
            try { events.put(JSObject(it.json)) } catch (e: Exception) { /* not an object: dropped by the page */ }
        }
        call.resolve(JSObject().put("events", events))
    }

    @PluginMethod
    fun ack(call: PluginCall) {
        val arr = call.getArray("ids") ?: JSArray()
        val ids = (0 until arr.length()).mapNotNull { arr.optString(it, null) }.toSet()
        bg(call) {
            val gone = WearPaths.inbox(context).remove(ids)
            val data = Wearable.getDataClient(context)
            for (it in gone) {
                val uri = it.uri ?: continue
                try { await(data.deleteDataItems(Uri.parse(uri))) } catch (e: Exception) { Log.w(WearPaths.TAG, "event item not deleted", e) }
            }
            call.resolve()
        }
    }

    @PluginMethod
    fun openOnWatch(call: PluginCall) = bg(call) {
        val nodes = try {
            await(Wearable.getCapabilityClient(context).getCapability(WearPaths.CAP_WATCH, CapabilityClient.FILTER_REACHABLE)).nodes
        } catch (e: Exception) { emptySet() }
        var ok = false
        for (n in nodes) {
            try { await(Wearable.getMessageClient(context).sendMessage(n.id, WearPaths.OPEN, ByteArray(0))); ok = true } catch (e: Exception) { /* next */ }
        }
        call.resolve(JSObject().put("ok", ok))
    }

    @PluginMethod
    fun sendClips(call: PluginCall) { call.resolve(JSObject().put("queued", 0)) }

    @PluginMethod
    fun dropClips(call: PluginCall) { call.resolve() }

    companion object {
        @Volatile private var live: WeakReference<WatchBridgePlugin>? = null

        /** A new event is in the inbox: a live page drains it now. */
        fun tellEvent() { live?.get()?.notifyListeners("watchEvent", JSObject()) }

        /** The watch app came or went: the page asks status() again. */
        fun tellState() { live?.get()?.notifyListeners("watchState", JSObject()) }
    }
}
