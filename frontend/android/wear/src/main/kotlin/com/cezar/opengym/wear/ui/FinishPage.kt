package com.cezar.opengym.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.cezar.opengym.watchcore.SessionCore
import com.cezar.opengym.watchcore.SessionState
import com.cezar.opengym.watchcore.WorkoutSummary
import com.cezar.opengym.wear.R
import com.cezar.opengym.wear.SessionStore
import com.cezar.opengym.wear.data.Values
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long Finish is held (RW-5.1): long enough not to happen by a brush of the sleeve. */
private const val HOLD_MS = 1500

private fun clock(sec: Long): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

/** Page 4 (the "Finalizar" mockup): sets and time so far, and Finish held for 1.5 s. No discard (RW-5.3). */
@Composable
fun FinishPage(state: SessionState, page: Int) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val w = state.workout ?: return
    val p = SessionCore.progress(state)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val held = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PageDots(4, page)
        Text(stringResource(R.string.finish_workout), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            Stat("${p.done}/${p.total}", stringResource(R.string.sets))
            w.start?.let { Stat(clock(((now - it) / 1000).toLong().coerceAtLeast(0)), stringResource(R.string.duration)) }
        }
        val label = stringResource(R.string.hold_to_finish)
        Box(
            Modifier.size(84.dp).semantics { contentDescription = label }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val run = scope.launch {
                            held.snapTo(0f)
                            held.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            SessionStore.finish(ctx)
                        }
                        tryAwaitRelease()
                        if (held.value < 1f) { run.cancel(); scope.launch { held.animateTo(0f, tween(200)) } }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val sz = Size(size.width - stroke, size.height - stroke)
                val tl = Offset(stroke / 2, stroke / 2)
                drawArc(Ui.surface, 0f, 360f, false, tl, sz, style = Stroke(stroke))
                drawArc(Ui.accent, -90f, 360f * held.value, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Box(Modifier.size(66.dp).clip(CircleShape).background(Ui.row), contentAlignment = Alignment.Center) {
                Text("⚑", fontSize = 26.sp, color = Ui.accent)
            }
        }
        Text(label, fontSize = 11.sp, color = Ui.dim, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = Ui.dim)
    }
}

/** The end of a workout: here, or on the phone while it ran here. */
@Composable
fun SummaryScreen(s: WorkoutSummary, onOk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(if (s.endedOnPhone) R.string.summary_phone else R.string.summary_done), fontSize = 13.sp, color = Ui.accent, fontWeight = FontWeight.SemiBold)
        Text(s.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("${s.sets}", stringResource(R.string.sets))
            s.duration?.let { Stat(clock(it.toLong()), stringResource(R.string.duration)) }
            if (s.volume > 0) Stat(Values.show(s.volume), s.unit)
        }
        if (s.prs.isNotEmpty()) Text(stringResource(R.string.summary_prs, s.prs.joinToString(", ")), fontSize = 11.sp, color = Color(0xFFFFD60A),
            textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.width(170.dp))
        Pill(stringResource(R.string.ok), Ui.accent, Ui.onAccent, Modifier.width(80.dp), onClick = onOk)
    }
}
