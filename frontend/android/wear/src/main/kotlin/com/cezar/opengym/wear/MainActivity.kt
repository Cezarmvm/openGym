package com.cezar.opengym.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.cezar.opengym.watchcore.SessionCore
import com.cezar.opengym.watchcore.SessionState
import java.text.DecimalFormat

/**
 * M2 minimal watch app (specs/fork-watch M2.8): the running workout as text and one button,
 * enough to prove the link end to end. The full screens (pager, bezel, rest) come in M3.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionStore.refreshFromPhone(this)
        setContent { MaterialTheme { Minimal() } }
    }
}

private val num = DecimalFormat("0.##")

@Composable
private fun Minimal() {
    val ctx = LocalContext.current
    val s by SessionStore.state(ctx).collectAsState()
    val incompatible by SessionStore.incompatible.collectAsState()
    var unsent by remember { mutableIntStateOf(0) }
    LaunchedEffect(s) { SessionStore.pendingEvents(ctx) { unsent = it } }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val state = s ?: SessionState()
        val w = state.workout
        when {
            incompatible -> Text(stringResource(R.string.update_app), textAlign = TextAlign.Center)
            w == null -> Text(stringResource(R.string.no_workout), textAlign = TextAlign.Center)
            else -> Workout(state, unsent)
        }
    }
}

@Composable
private fun Workout(state: SessionState, unsent: Int) {
    val ctx = LocalContext.current
    val w = state.workout ?: return
    val ei = state.entryIndex
    if (ei >= w.entries.size) {
        Text(stringResource(R.string.all_done), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Button(onClick = { SessionStore.finish(ctx) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.finish)) }
        return
    }
    val e = w.entries[ei]
    val si = SessionCore.setIndex(state, ei)
    Text(w.name, fontSize = 12.sp, color = MaterialTheme.colors.secondary)
    Text(e.name, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 2)
    if (si == null) {
        Text(stringResource(R.string.all_done))
        Button(onClick = { SessionStore.goTo(ctx, SessionCore.next(state, ei, e.sets.size - 1).entry) }) { Text("›") }
        return
    }
    val row = e.sets[si]
    val work = e.sets.filter { !it.warmup }
    val label = if (row.warmup) stringResource(R.string.warmup)
        else stringResource(R.string.set_of, e.sets.take(si + 1).count { !it.warmup }, work.size)
    Text(label, fontSize = 13.sp)
    val side = row.sideUp
    val shown = side?.let { row.sides!![it] }
    val wv = shown?.w ?: row.w
    val rv = shown?.r ?: row.r
    val numbers = when {
        e.isTimed -> "${num.format(row.sec ?: 0.0)} s"
        e.isCardio -> "—"
        else -> "${num.format(wv ?: 0.0)} ${w.unit} × ${num.format(rv ?: 0.0)}"
    }
    Text((side?.let { "$it · " } ?: "") + numbers, fontSize = 22.sp)
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            when {
                e.isTimed -> SessionStore.tick(ctx, ei, si, w = null, r = null, sec = row.sec)
                e.isCardio -> SessionStore.tick(ctx, ei, si, w = null, r = null)
                else -> SessionStore.tick(ctx, ei, si, side = side, w = wv, r = rv)
            }
        },
        colors = ButtonDefaults.primaryButtonColors(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.complete_set)) }
    if (unsent > 0) Text("↑ $unsent", fontSize = 11.sp, color = MaterialTheme.colors.secondary)
}
