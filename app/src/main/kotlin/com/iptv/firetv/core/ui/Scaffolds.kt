package com.iptv.firetv.core.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Date

/** True while the app-level top bar is shown above the screen (it already supplies the top safe margin). */
val LocalTopBarVisible = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Where focus should land when the user presses Down from the top bar. A screen registers the control it considers its
 * "front door" (Live TV: the selected category), so Down never jumps to an arbitrary field such as the search box.
 */
class ContentEntry { var requester: FocusRequester? by mutableStateOf(null) }
val LocalContentEntry = staticCompositionLocalOf { ContentEntry() }

@Composable
fun RegisterContentEntry(requester: FocusRequester) {
    val holder = LocalContentEntry.current
    DisposableEffect(requester) { holder.requester = requester; onDispose { if (holder.requester === requester) holder.requester = null } }
}

/** Standard screen frame: overscan-safe padding on every screen. */
@Composable
fun ScreenFrame(modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().padding(start = Dimens.SafeH, end = Dimens.SafeH, top = if (LocalTopBarVisible.current) 8.dp else Dimens.SafeV, bottom = Dimens.SafeV)) {
        if (title != null) Text(title, Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
fun rememberClock(): State<String> {
    val ctx = LocalContext.current
    return produceState("") {
        val fmt = DateFormat.getTimeFormat(ctx)
        while (true) { value = fmt.format(Date()); delay(30_000) }
    }
}

fun formatTime(ctx: android.content.Context, ms: Long): String = DateFormat.getTimeFormat(ctx).format(Date(ms))
fun formatDuration(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
