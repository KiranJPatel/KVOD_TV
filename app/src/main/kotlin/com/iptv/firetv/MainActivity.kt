package com.iptv.firetv

import android.Manifest
import android.app.PictureInPictureParams
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.iptv.firetv.app.PipState
import com.iptv.firetv.app.SearchInbox
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptv.firetv.app.IptvRoot
import com.iptv.firetv.app.MainViewModel
import com.iptv.firetv.app.ShareInbox
import com.iptv.firetv.core.ui.IptvTheme
import com.iptv.firetv.data.AppSettings
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* notifications are optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        // Keep the TV awake only while a stream is actually playing; otherwise the normal screensaver / sleep timers apply.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.keepAwake.collect { on ->
                    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings ?: AppSettings()
            val lang = com.iptv.firetv.core.common.I18n.resolve(s.language, java.util.Locale.getDefault().language)
            androidx.compose.runtime.remember(lang) { com.iptv.firetv.core.common.I18n.load(applicationContext, lang); lang }
            // key(lang): switching language recomposes the whole tree with the new strings.
            androidx.compose.runtime.key(lang) {
                IptvTheme(s.theme, s.themeMode, s.accent, s.textScale, s.uiScale) {
                    if (settings == null) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) else IptvRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleShare(intent) }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEARCH) i.getStringExtra(SearchManager.QUERY)?.trim()?.takeIf { it.isNotEmpty() }?.let { SearchInbox.pendingQuery.value = it.take(80) }
        if (i?.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.startsWith("http", true) }?.let { ShareInbox.pendingUrl.value = it }
    }

    /** Pressing Home while a stream is playing keeps it visible in Picture-in-picture where the device supports it. */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipState.playerVisible.value && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.active.value = isInPictureInPictureMode
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean { vm.touch(); return super.dispatchKeyEvent(event) }

    /** Decoders and sockets are released when the app leaves the foreground; the player screen restores playback on return. */
    override fun onStop() { super.onStop(); vm.releasePlayer() }
}
