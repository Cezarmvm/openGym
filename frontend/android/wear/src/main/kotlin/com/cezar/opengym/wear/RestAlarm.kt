package com.cezar.opengym.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * The end of a rest taps the wrist with the screen off or dim too (RW-3.3): an exact alarm,
 * set again whenever the rest changes and cancelled when it is skipped or over.
 */
object RestAlarm {
    private fun intent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, RestAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun set(ctx: Context, endsAt: Long?) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(ctx)
        am.cancel(pi)
        if (endsAt == null || endsAt <= System.currentTimeMillis()) return
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
    }

    fun buzz(ctx: Context, pattern: LongArray = longArrayOf(0, 300, 150, 300)) {
        val v = ctx.getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val s = SessionStore.state(ctx).value
        if (s?.workout != null && s.settings.restHaptics != false) RestAlarm.buzz(ctx)
        SessionStore.restOver(ctx)
    }
}
