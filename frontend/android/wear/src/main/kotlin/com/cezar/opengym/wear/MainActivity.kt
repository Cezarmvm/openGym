package com.cezar.opengym.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.cezar.opengym.watchcore.SessionState
import com.cezar.opengym.wear.data.RestClock
import com.cezar.opengym.wear.ui.FinishPage
import com.cezar.opengym.wear.ui.InAppLanguage
import com.cezar.opengym.wear.ui.ListPage
import com.cezar.opengym.wear.ui.RestPage
import com.cezar.opengym.wear.ui.SetPage
import com.cezar.opengym.wear.ui.SummaryScreen
import com.cezar.opengym.wear.ui.Ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The watch app (specs/fork-watch M3): four pages, Set · Rest · List · Finish, over SessionStore. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionStore.refreshFromPhone(this)
        setContent { MaterialTheme { App() } }
    }
}

private const val SET = 0
private const val REST = 1
private const val FINISH = 3

@Composable
private fun App() {
    val ctx = LocalContext.current
    val s by SessionStore.state(ctx).collectAsState()
    val rest by SessionStore.rest(ctx).collectAsState()
    val incompatible by SessionStore.incompatible.collectAsState()
    val summary by SessionStore.summary.collectAsState()
    val state = s ?: SessionState()
    var offline by remember { mutableStateOf(false) }
    var unsent by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) { SessionStore.pendingEvents(ctx) { unsent = it } }
    LaunchedEffect(Unit) {
        while (true) { SessionStore.phoneConnected(ctx) { offline = !it }; delay(20_000) }
    }
    InAppLanguage(state.settings.lang) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val sum = summary
            val w = state.workout
            when {
                incompatible -> Message(stringResource(R.string.update_app))
                sum != null -> SummaryScreen(sum) { SessionStore.summary.value = null }
                w == null -> Message(stringResource(R.string.no_workout))
                else -> Pages(state, rest, offline, unsent)
            }
        }
    }
}

@Composable
private fun Message(text: String) = Text(text, textAlign = TextAlign.Center, fontSize = 15.sp, color = Ui.dim, modifier = Modifier.padding(24.dp))

@Composable
private fun Pages(state: SessionState, rest: com.cezar.opengym.wear.data.Rest?, offline: Boolean, unsent: Int) {
    val pager = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    // A rest that starts brings its page up; when it is over (or skipped) the set comes back.
    LaunchedEffect(rest?.endsAt) {
        val r = rest
        if (RestClock.running(r, System.currentTimeMillis())) {
            pager.animateScrollToPage(REST)
            delay(RestClock.left(r, System.currentTimeMillis()))
        }
        if (pager.currentPage == REST) pager.animateScrollToPage(SET)
    }
    // Every set done: the Finish page.
    val allDone = state.workout?.let { state.entryIndex >= it.entries.size } ?: false
    LaunchedEffect(allDone) { if (allDone && !RestClock.running(rest, System.currentTimeMillis())) pager.animateScrollToPage(FINISH) }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            SET -> SetPage(state, page, active = pager.currentPage == SET, offline = offline, unsent = unsent,
                goFinish = { scope.launch { pager.animateScrollToPage(FINISH) } })
            REST -> RestPage(state, rest, page)
            2 -> ListPage(state, page) { scope.launch { pager.animateScrollToPage(SET) } }
            else -> FinishPage(state, page)
        }
    }
}
