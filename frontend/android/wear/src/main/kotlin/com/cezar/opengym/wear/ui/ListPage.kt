package com.cezar.opengym.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import com.cezar.opengym.watchcore.SessionCore
import com.cezar.opengym.watchcore.SessionState
import com.cezar.opengym.wear.R
import com.cezar.opengym.wear.SessionStore

/** Page 3, the exercises (the "Lista" mockup): done/total each, a tap goes to it on the watch. */
@Composable
fun ListPage(state: SessionState, page: Int, toSet: () -> Unit) {
    val ctx = LocalContext.current
    val w = state.workout ?: return
    val p = SessionCore.progress(state)
    val listState = rememberScalingLazyListState(initialCenterItemIndex = (state.entryIndex + 1).coerceAtMost(w.entries.size))
    ScalingLazyColumn(state = listState, modifier = androidx.compose.ui.Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PageDots(4, page)
                Text(stringResource(R.string.progress_sets, w.name, p.done, p.total), fontSize = 12.sp, color = Ui.dim,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
        itemsIndexed(w.entries) { i, e ->
            val work = e.sets.filter { !it.warmup }
            val done = work.count { it.done }
            val current = i == state.entryIndex
            val complete = work.isNotEmpty() && done == work.size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (current) Ui.rowOn else Ui.row)
                    .then(if (current) Modifier.border(2.dp, Ui.accent, RoundedCornerShape(24.dp)) else Modifier)
                    .clickable { SessionStore.goTo(ctx, i); toSet() }
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    (if (complete) "✓ " else "") + e.name, fontSize = 14.sp,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text("$done/${work.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (current || complete) Ui.accent else Ui.dim, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
