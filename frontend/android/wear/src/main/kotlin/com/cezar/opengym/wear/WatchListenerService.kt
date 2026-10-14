package com.cezar.opengym.wear

import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Woken by Play services with the app closed: the phone's snapshot goes into the session (so the
 * app opens where the phone is), and "a workout started" taps the wrist once (D-6; the ongoing
 * activity comes in M4). An event item deleted by the phone is its confirmation: logged.
 */
class WatchListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (e in events) {
            val path = e.dataItem.uri.path ?: continue
            when {
                path == SessionStore.SNAPSHOT && e.type == DataEvent.TYPE_CHANGED -> {
                    val json = DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json") ?: continue
                    SessionStore.applySnapshotJson(this, json)
                    Log.i(SessionStore.TAG, "watch: snapshot ${json.length} bytes")
                }
                path.startsWith(SessionStore.EVENT_PREFIX) && e.type == DataEvent.TYPE_DELETED ->
                    Log.i(SessionStore.TAG, "watch: phone applied ${path.removePrefix(SessionStore.EVENT_PREFIX)}")
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/opengym/v1/open") return
        val v = getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
