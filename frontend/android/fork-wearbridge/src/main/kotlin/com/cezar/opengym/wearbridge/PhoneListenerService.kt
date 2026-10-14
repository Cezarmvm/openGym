package com.cezar.opengym.wearbridge

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import java.io.File

/** The Data Layer paths and capabilities both apps agree on (specs/fork-watch design §3.1). */
object WearPaths {
    const val SNAPSHOT = "/opengym/v1/snapshot"
    const val EVENT_PREFIX = "/opengym/v1/event/"
    const val OPEN = "/opengym/v1/open"
    const val CAP_WATCH = "opengym_watch"
    const val TAG = "OpenGymWear"

    fun inbox(ctx: Context) = EventInbox(File(ctx.filesDir, EventInbox.FILE_NAME))

    /** An event DataItem into the inbox. Returns whether it was new. */
    fun keep(ctx: Context, item: DataItem): Boolean {
        val path = item.uri.path ?: return false
        if (!path.startsWith(EVENT_PREFIX)) return false
        val json = DataMapItem.fromDataItem(item).dataMap.getString("json") ?: return false
        val id = EventInbox.idOf(json) ?: return false
        return inbox(ctx).add(EventInbox.Item(id = id, json = json, uri = item.uri.toString(), at = System.currentTimeMillis()))
    }
}

/**
 * Play services wakes this when the watch sends an event or its app comes and goes, with the
 * phone app in the background or not running at all. Each event goes to the inbox first; a live
 * page is then told (watchEvent), and one that is asleep drains the inbox when it wakes.
 */
class PhoneListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        var fresh = 0
        for (e in events) {
            if (e.type != DataEvent.TYPE_CHANGED) continue
            try { if (WearPaths.keep(this, e.dataItem)) fresh++ } catch (x: Exception) { Log.w(WearPaths.TAG, "event not kept", x) }
        }
        if (fresh > 0) {
            Log.i(WearPaths.TAG, "phone: $fresh event(s) in the inbox")
            WatchBridgePlugin.tellEvent()
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        WatchBridgePlugin.tellState()
    }
}
