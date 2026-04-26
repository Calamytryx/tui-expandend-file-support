package com.rama.tui.managers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Environment
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.rama.tui.Track
import java.io.File

object MusicManager {

    private var player: MediaPlayer? = null
    private var mediaSession: MediaSession? = null

    var tracks: List<Track> = emptyList()
        private set

    var allTracks: List<Track> = emptyList()
        private set

    var currentIndex: Int = -1
        private set

    var isPlaying: Boolean = false
        private set

    var isRepeat: Boolean = false

    var onStateChanged: (() -> Unit)? = null

    val currentTrack: Track? get() = tracks.getOrNull(currentIndex)

    // region Media Session

    fun initMediaSession(context: Context) {
        mediaSession = MediaSession(context, "TuiMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!isPlaying) togglePlayPause()
                }

                override fun onPause() {
                    if (isPlaying) togglePlayPause()
                }

                override fun onSkipToNext() {
                    next()
                }

                override fun onSkipToPrevious() {
                    prev()
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event =
                        mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (event?.action == KeyEvent.ACTION_DOWN) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY -> onPlay()
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> onPause()
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> togglePlayPause()
                            KeyEvent.KEYCODE_MEDIA_NEXT -> next()
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> prev()
                        }
                    }
                    return true
                }
            })
            isActive = true
        }
    }

    fun releaseMediaSession() {
        mediaSession?.release()
        mediaSession = null
    }

    private fun updatePlaybackState() {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, player?.currentPosition?.toLong() ?: 0L, 1f)
                .setActions(
                    PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )
    }
    
    // region Permissions

    fun hasPermission(context: Context?): Boolean {
        if (context == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    // region Track Loading

    fun loadTracks(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val dirs = getStorageVolumes(context)
        allTracks = dirs.flatMap { scanDir(it) }.sortedBy { it.title.lowercase() }
        tracks = allTracks
        if (tracks.isNotEmpty() && currentIndex < 0) currentIndex = 0
        return true
    }

    private fun getStorageVolumes(context: Context): List<File> {
        val volumes = mutableListOf<File>()

        volumes.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))

        context.getExternalFilesDirs(Environment.DIRECTORY_MUSIC)
            .filterNotNull()
            .forEach { appSpecificDir ->
                // Path: /storage/<id>/Android/data/<pkg>/files/Music — 5 levels up = volume root
                val volumeRoot =
                    appSpecificDir.parentFile?.parentFile?.parentFile?.parentFile?.parentFile
                val musicDir = volumeRoot?.let { File(it, Environment.DIRECTORY_MUSIC) }
                if (musicDir?.exists() == true) volumes.add(musicDir)
            }

        return volumes.distinct().filter { it.exists() && it.isDirectory }
    }

    private fun scanDir(dir: File): List<Track> =
        dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in Track.AUDIO_EXTENSIONS }
            ?.map { Track.fromFile(it) }
            ?.sortedBy { it.title.lowercase() }
            ?: emptyList()

    // region Playback Control

    fun play(index: Int = currentIndex) {
        if (tracks.isEmpty() || index !in tracks.indices) return
        currentIndex = index

        player?.release()
        player = MediaPlayer().apply {
            setDataSource(tracks[index].file.absolutePath)
            setOnCompletionListener { onTrackFinished() }
            prepare()
            start()
        }
        isPlaying = true
        onStateChanged?.invoke()
        updatePlaybackState()
    }

    fun togglePlayPause() {
        val p = player
        if (p == null) {
            play()
            return
        }
        if (isPlaying) {
            p.pause()
            isPlaying = false
        } else {
            p.start()
            isPlaying = true
        }
        onStateChanged?.invoke()
        updatePlaybackState()
    }

    fun next() {
        if (tracks.isEmpty()) return
        currentIndex = when {
            currentIndex < tracks.lastIndex -> currentIndex + 1
            isRepeat -> 0
            else -> return
        }
        play(currentIndex)
    }

    fun prev() {
        if (tracks.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else tracks.lastIndex
        play(currentIndex)
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        p.seekTo((p.duration * fraction.coerceIn(0f, 1f)).toInt())
    }

    fun release() {
        player?.release()
        player = null
        isPlaying = false
        onStateChanged?.invoke()
        updatePlaybackState()
    }

    // region Track Management

    fun setTracks(newTracks: List<Track>, index: Int = 0) {
        tracks = newTracks
        play(index)
    }

    fun shuffleTracks() {
        if (tracks.isEmpty()) return
        val current = currentTrack
        val rest = tracks.toMutableList().also {
            if (current != null) it.remove(current)
        }.shuffled()
        tracks = if (current != null) listOf(current) + rest else rest
        currentIndex = 0
        onStateChanged?.invoke()
    }

    fun playerProgress(): Float? {
        val p = player ?: return null
        if (p.duration <= 0) return null
        return p.currentPosition.toFloat() / p.duration
    }

    // region Internal

    private fun onTrackFinished() {
        when {
            isRepeat -> play(currentIndex)
            else -> next()
        }
    }
}