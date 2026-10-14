package com.cezar.opengym.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.cezar.opengym.watchcore.SessionCore
import com.cezar.opengym.watchcore.SessionState
import com.cezar.opengym.wear.R
import com.cezar.opengym.wear.SessionStore
import com.cezar.opengym.wear.data.Rest
import com.cezar.opengym.wear.data.RestClock
import com.cezar.opengym.wear.data.Values
import kotlinx.coroutines.delay

/** Page 2, the rest (the "Descanso" mockup): the ring, the time left, what comes next. */
@Composable
fun RestPage(state: SessionState, rest: Rest?, page: Int) {
    val ctx = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(rest) {
        while (RestClock.running(rest, System.currentTimeMillis())) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    val left = RestClock.left(rest, now)
    val ringColor = Ui.accent
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (rest != null && left > 0) {
            val frac = (left.toFloat() / rest.total.coerceAtLeast(1)).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val stroke = 5.dp.toPx()
                val sz = Size(size.width - stroke, size.height - stroke)
                val tl = Offset(stroke / 2, stroke / 2)
                drawArc(Color(0xFF1F2327), 0f, 360f, false, tl, sz, style = Stroke(stroke))
                drawArc(ringColor, -90f, 360f * frac, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            PageDots(4, page)
            if (rest == null || left <= 0) {
                Text(stringResource(R.string.no_rest), fontSize = 14.sp, color = Ui.dim)
                return@Column
            }
            Text(stringResource(R.string.rest), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ui.accent, letterSpacing = 1.5.sp)
            Text(RestClock.label(left), fontSize = 40.sp, fontWeight = FontWeight.Bold)
            val w = state.workout
            val e = w?.entries?.getOrNull(state.entryIndex)
            val si = if (e != null) SessionCore.setIndex(state, state.entryIndex) else null
            if (e != null && si != null && state.settings.show?.showNext != false) {
                val row = e.sets[si]
                val number = e.sets.take(si + 1).count { !it.warmup }.coerceAtLeast(1)
                Text(stringResource(R.string.next_up, e.name, number), fontSize = 11.sp, color = Ui.dim, textAlign = TextAlign.Center,
                    maxLines = 2, modifier = Modifier.width(170.dp))
                if (!e.isCardio) {
                    val numbers = if (e.isTimed) "${Values.show(row.sec, locale)} s"
                        else "${Values.show(row.w, locale)} ${w.unit} × ${Values.show(row.r, locale)}"
                    Text(numbers, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 6.dp)) {
                Pill(stringResource(R.string.minus15), Ui.surface, Color.White, Modifier.width(50.dp)) { SessionStore.adjustRest(ctx, -15) }
                Pill(stringResource(R.string.skip), Color.White, Color.Black, Modifier.width(62.dp)) { SessionStore.skipRest(ctx) }
                Pill(stringResource(R.string.plus15), Ui.surface, Color.White, Modifier.width(50.dp)) { SessionStore.adjustRest(ctx, +15) }
            }
        }
    }
}
