package com.rama.tui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.rama.tui.managers.MusicManager

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (event.action != KeyEvent.ACTION_DOWN) return

        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (!MusicManager.isPlaying) MusicManager.togglePlayPause()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> if (MusicManager.isPlaying) MusicManager.togglePlayPause()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> MusicManager.togglePlayPause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> MusicManager.next()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MusicManager.prev()
        }
    }
}