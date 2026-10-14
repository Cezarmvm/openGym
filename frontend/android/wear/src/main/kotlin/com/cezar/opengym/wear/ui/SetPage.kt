package com.cezar.opengym.wear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.cezar.opengym.watchcore.Entry
import com.cezar.opengym.watchcore.SessionCore
import com.cezar.opengym.watchcore.SessionState
import com.cezar.opengym.wear.R
import com.cezar.opengym.wear.SessionStore
import com.cezar.opengym.wear.data.Values
import com.cezar.opengym.wear.data.Values.Field
import kotlin.math.abs

/** Bezel pixels per step: about one detent of the Galaxy Watch's rotating bezel. */
private const val ROTARY_STEP_PX = 48f

/**
 * Page 1, the set (design §8; the "Série" and "Unilateral" mockups): what to lift now, the weight
 * and reps the phone planned, moved by the bezel (the field in focus) or +/−, and Done.
 * Done = SessionCore.tick with these numbers; the next set, the other side, the superset's turn
 * and the rest all come from SessionCore.next. A long press on Done skips the set (upstream's skip).
 */
@Composable
fun SetPage(state: SessionState, page: Int, active: Boolean, offline: Boolean, unsent: Int, goFinish: () -> Unit) {
    val ctx = LocalContext.current
    val w = state.workout ?: return
    val ei = state.entryIndex
    Column(
        Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PageDots(4, page)
        Status(offline, unsent)
        val e = w.entries.getOrNull(ei)
        val si = if (e != null) SessionCore.setIndex(state, ei) else null
        if (e == null || si == null) {
            Spacer(Modifier.height(10.dp))
            if (e != null) Title(e.name)
            Text(stringResource(R.string.all_done), fontSize = 14.sp, color = Ui.dim)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ei > 0) Arrow(left = true) { SessionStore.goTo(ctx, ei - 1) }
                Pill(stringResource(R.string.finish), Ui.accent, Ui.onAccent, Modifier.width(110.dp), onClick = goFinish)
                if (e != null && ei + 1 < w.entries.size) Arrow(left = false) { SessionStore.goTo(ctx, ei + 1) }
            }
            return@Column
        }
        TheSet(state, e, ei, si, w.unit, active)
    }
}

@Composable
private fun Status(offline: Boolean, unsent: Int) {
    val parts = listOfNotNull(
        if (offline) stringResource(R.string.offline) else null,
        if (unsent > 0) stringResource(R.string.unsent, unsent) else null,
    )
    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), fontSize = 10.sp, color = Color(0xFFFF9F0A))
}

@Composable
private fun Title(name: String) = Text(
    name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 16.dp),
)

@Composable
private fun TheSet(state: SessionState, e: Entry, ei: Int, si: Int, unit: String, active: Boolean) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val locale = LocalConfiguration.current.locales[0]
    val row = e.sets[si]
    val side = row.sideUp
    val shown = side?.let { row.sides!![it] }
    val key = "${state.workout?.id}#$ei#$si#${side ?: ""}"
    // The numbers being edited, starting from the phone's (reset for every set and side).
    var wv by remember(key) { mutableStateOf(shown?.w ?: row.w) }
    var rv by remember(key) { mutableStateOf(shown?.r ?: row.r) }
    var sv by remember(key) { mutableStateOf(row.sec) }
    val fields = when {
        e.isCardio -> emptyList()
        e.isTimed -> listOf(Field.SEC)
        else -> listOf(Field.WEIGHT, Field.REPS)
    }
    val first = if (state.settings.crown == "weight") Field.WEIGHT else Field.REPS
    var focus by remember(key) { mutableStateOf(if (first in fields) first else fields.firstOrNull()) }
    fun move(f: Field, steps: Int) {
        val step = Values.stepOf(e, unit, f)
        when (f) {
            Field.WEIGHT -> wv = Values.move(wv, steps, step)
            Field.REPS -> rv = Values.move(rv, steps, step)
            Field.SEC -> sv = Values.move(sv, steps, step)
        }
    }
    val requester = remember { FocusRequester() }
    var acc by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, key) { if (active) runCatching { requester.requestFocus() } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .onRotaryScrollEvent { ev ->
                val f = focus ?: return@onRotaryScrollEvent false
                // The Galaxy Watch bezel clicks: one detent is one event, one step (clockwise up),
                // however many pixels the system says it scrolled. A smooth crown adds up instead.
                acc += ev.verticalScrollPixels
                if (abs(ev.verticalScrollPixels) >= ROTARY_STEP_PX || abs(acc) >= ROTARY_STEP_PX) {
                    move(f, if (acc > 0) 1 else -1)
                    acc = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                true
            }
            .focusRequester(requester)
            .focusable(),
    ) {
        Title(e.name)
        val work = e.sets.filter { !it.warmup }
        val label = buildString {
            if (side != null) append(stringResource(if (side == "R") R.string.side_R else R.string.side_L)).append(" · ")
            append(if (row.warmup) stringResource(R.string.warmup) else stringResource(R.string.set_of, e.sets.take(si + 1).count { !it.warmup }, work.size))
            e.group?.let { append(" · SS ").append(it.uppercase()) }
        }
        Text(label, fontSize = 11.sp, color = Ui.dim)
        val unitLabel = stringResource(if (unit == "lb") R.string.lb else R.string.kg)
        for (f in fields) {
            val (v, u) = when (f) {
                Field.WEIGHT -> wv to unitLabel
                Field.REPS -> rv to stringResource(R.string.reps)
                Field.SEC -> sv to stringResource(R.string.sec)
            }
            Stepper(Values.show(v, locale), u, focused = focus == f, onFocus = { focus = f }, onMinus = { move(f, -1) }, onPlus = { move(f, +1) })
        }
        if (e.isCardio) Text(stringResource(R.string.cardio_on_phone), fontSize = 12.sp, color = Ui.dim, textAlign = TextAlign.Center)
        val ctxDone = if (side != null) stringResource(R.string.complete_side, stringResource(if (side == "R") R.string.side_R else R.string.side_L)) else stringResource(R.string.complete)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Arrow(left = true, enabled = ei > 0) { SessionStore.goTo(ctx, ei - 1) }
            DoneButton(ctxDone,
                onDone = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    when {
                        e.isCardio -> SessionStore.tick(ctx, ei, si, w = null, r = null)
                        e.isTimed -> SessionStore.tick(ctx, ei, si, w = null, r = null, sec = sv)
                        else -> SessionStore.tick(ctx, ei, si, side = side, w = wv, r = rv)
                    }
                },
                onSkip = { SessionStore.skip(ctx, ei, si) },
            )
            Arrow(left = false, enabled = true) { SessionStore.goTo(ctx, ei + 1) }
        }
    }
}

@Composable
private fun Stepper(value: String, unit: String, focused: Boolean, onFocus: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.width(148.dp).height(40.dp)
            .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Ui.accent else Ui.outline), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onFocus)
            .padding(horizontal = 4.dp),
    ) {
        Round("−", stringResource(R.string.decrease), 32, onMinus)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(" $unit", fontSize = 11.sp, color = Ui.dim, modifier = Modifier.padding(bottom = 4.dp))
        }
        Round("+", stringResource(R.string.increase), 32, onPlus)
    }
}

@Composable
private fun Round(text: String, label: String, sizeDp: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(Ui.surface).clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Arrow(left: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val label = stringResource(if (left) R.string.prev_exercise else R.string.next_exercise)
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Ui.surface)
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(if (left) "‹" else "›", fontSize = 20.sp, color = if (enabled) Color(0xFFD9DDE1) else Ui.dot) }
}

/** Done; a long press skips the set instead (a short haptic says so). */
@Composable
private fun DoneButton(label: String, onDone: () -> Unit, onSkip: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.width(92.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(Ui.accent)
            .pointerInput(onDone, onSkip) {
                detectTapGestures(
                    onTap = { onDone() },
                    onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSkip() },
                )
            },
        contentAlignment = Alignment.Center,
    ) { Text("✓ $label", color = Ui.onAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1) }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1) }
}
