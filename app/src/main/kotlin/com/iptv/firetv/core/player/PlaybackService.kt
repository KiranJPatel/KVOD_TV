package com.iptv.firetv.core.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts the app-wide [MediaSession] so the system can show the media notification and route transport commands
 * (pause/resume/stop from the notification, remote or assistant). The player itself stays in [PlayerRepository].
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var repo: PlayerRepository

    override fun onCreate() { super.onCreate(); repo.session?.let { addSession(it) } }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = repo.session
    override fun onTaskRemoved(rootIntent: Intent?) { repo.release(); stopSelf() }
}
