package com.rama.tui.activities

import android.app.Activity
import android.app.Dialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.IntentSender
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.rama.tui.R
import com.rama.tui.Track
import com.rama.tui.managers.FontManager
import com.rama.tui.widgets.WdButton
import java.io.File

object TrackEditDialog {

    private const val TAG = "TrackEditDialog"

    // Request codes — handled in MainActivity.onActivityResult
    const val REQUEST_RENAME = 1001
    const val REQUEST_DELETE = 1002
    const val REQUEST_STRIP  = 1003

    // State held across the permission grant round-trip
    private var pendingTrack: Track?            = null
    private var pendingNewFileName: String?     = null
    private var pendingOnChanged: (() -> Unit)? = null

    fun show(activity: Activity, track: Track, onChanged: () -> Unit) {
        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_track_edit)

        dialog.window?.let { win ->
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(dm)
            val params = win.attributes
            params.width     = ViewGroup.LayoutParams.MATCH_PARENT
            params.height    = ViewGroup.LayoutParams.WRAP_CONTENT
            params.gravity   = Gravity.CENTER
            params.dimAmount = 0.6f
            win.attributes   = params
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        val titleInput    = dialog.findViewById<EditText>(R.id.title)
        val artistInput   = dialog.findViewById<EditText>(R.id.artist)
        val countryInput  = dialog.findViewById<EditText>(R.id.country)
        val languageInput = dialog.findViewById<EditText>(R.id.language)
        val summaryView   = dialog.findViewById<TextView>(R.id.display)
        val metadataView  = dialog.findViewById<TextView>(R.id.display_metadata)

        val deleteMetaBtn = dialog.findViewById<WdButton>(R.id.delete_metadata_button)
        val deleteSongBtn = dialog.findViewById<WdButton>(R.id.delete_button)
        val updateBtn     = dialog.findViewById<WdButton>(R.id.update_button)
        val cancelBtn     = dialog.findViewById<WdButton>(R.id.cancel_button)

        titleInput.setText(track.title)
        artistInput.setText(track.artists.joinToString(", "))
        countryInput.setText(track.countries.joinToString(", "))
        languageInput.setText(track.languages.joinToString(", "))

        fun refreshSummary() {
            val t = titleInput.text.toString().trim()
            val a = artistInput.text.toString().trim()
            val c = countryInput.text.toString().trim()
            val l = languageInput.text.toString().trim()
            summaryView.text = buildString {
                if (a.isNotEmpty()) { append(a); append(" - ") }
                append(t)
                if (c.isNotEmpty()) { append(" - "); append(c) }
                if (l.isNotEmpty()) { append(" - "); append(l) }
            }
        }
        refreshSummary()

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = refreshSummary()
        }
        titleInput.addTextChangedListener(watcher)
        artistInput.addTextChangedListener(watcher)
        countryInput.addTextChangedListener(watcher)
        languageInput.addTextChangedListener(watcher)

        val embeddedMeta = readEmbeddedMetadata(track.file)
        metadataView.text = if (embeddedMeta.isEmpty()) {
            "(no embedded metadata found)"
        } else {
            embeddedMeta.entries.joinToString("\n") { (k, v) -> "$k: $v" }
        }

        FontManager.applyToView(activity, dialog.findViewById(android.R.id.content))

        // ── Strip metadata ───────────────────────────────────────────────────
        deleteMetaBtn.setOnClickListener {
            if (embeddedMeta.isEmpty()) {
                Toast.makeText(activity, "No embedded metadata to remove", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingTrack     = track
            pendingOnChanged = onChanged
            val uri = resolveAudioUri(activity, track.file)
            if (uri == null) {
                // Not in MediaStore (e.g. very fresh scan) — attempt direct write
                val ok = stripEmbeddedMetadata(activity, track)
                handleStripResult(activity, ok, metadataView, onChanged)
                return@setOnClickListener
            }
            requestWriteAccess(activity, listOf(uri), REQUEST_STRIP) {
                // Runs immediately on Android < 11 when manifest perm is enough
                val ok = stripEmbeddedMetadata(activity, track)
                handleStripResult(activity, ok, metadataView, onChanged)
            }
        }

        // ── Delete song ──────────────────────────────────────────────────────
        deleteSongBtn.setOnClickListener {
            pendingTrack     = track
            pendingOnChanged = onChanged
            val uri = resolveAudioUri(activity, track.file)
            if (uri != null) {
                requestWriteAccess(activity, listOf(uri), REQUEST_DELETE) {
                    doDelete(activity, uri, onChanged, dialog)
                }
            } else {
                // Fallback: plain file delete
                if (track.file.delete()) {
                    Toast.makeText(activity, "Track deleted", Toast.LENGTH_SHORT).show()
                    onChanged(); dialog.dismiss()
                } else {
                    Toast.makeText(activity, "Could not delete file", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ── Rename ───────────────────────────────────────────────────────────
        updateBtn.setOnClickListener {
            val newTitle     = titleInput.text.toString().trim()
            val newArtists   = artistInput.text.toString().trim()
            val newCountries = countryInput.text.toString().trim()
            val newLanguages = languageInput.text.toString().trim()

            if (newTitle.isEmpty()) {
                Toast.makeText(activity, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newFileName = buildString {
                if (newArtists.isNotEmpty())   { append(newArtists);   append(" - ") }
                append(newTitle)
                if (newCountries.isNotEmpty()) { append(" - "); append(newCountries) }
                if (newLanguages.isNotEmpty()) { append(" - "); append(newLanguages) }
            } + ".${track.ext}"

            pendingTrack       = track
            pendingNewFileName = newFileName
            pendingOnChanged   = onChanged

            val uri = resolveAudioUri(activity, track.file)
            if (uri != null) {
                requestWriteAccess(activity, listOf(uri), REQUEST_RENAME) {
                    doRename(activity, uri, newFileName, onChanged, dialog)
                }
            } else {
                // Fallback: plain file rename (works on Android ≤ 9)
                val dest = File(track.file.parent, newFileName)
                if (track.file.renameTo(dest)) {
                    Toast.makeText(activity, "Track renamed", Toast.LENGTH_SHORT).show()
                    onChanged(); dialog.dismiss()
                } else {
                    Toast.makeText(activity, "Could not rename file", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ── onActivityResult relay (call from MainActivity) ──────────────────────

    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            pendingTrack = null; pendingNewFileName = null; pendingOnChanged = null
            return
        }
        val track     = pendingTrack     ?: return
        val onChanged = pendingOnChanged ?: return
        val uri       = resolveAudioUri(activity, track.file)

        when (requestCode) {
            REQUEST_RENAME -> {
                val newFileName = pendingNewFileName ?: return
                if (uri != null) doRename(activity, uri, newFileName, onChanged, null)
                else Toast.makeText(activity, "Could not locate file", Toast.LENGTH_SHORT).show()
            }
            REQUEST_DELETE -> {
                if (uri != null) doDelete(activity, uri, onChanged, null)
                else Toast.makeText(activity, "Could not locate file", Toast.LENGTH_SHORT).show()
            }
            REQUEST_STRIP -> {
                val ok = stripEmbeddedMetadata(activity, track)
                Toast.makeText(
                    activity,
                    if (ok) "Metadata removed" else "Failed to remove metadata",
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) onChanged()
            }
        }
        pendingTrack = null; pendingNewFileName = null; pendingOnChanged = null
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Resolves a MediaStore.Audio.Media URI for the given file.
     * Audio URIs are required by createWriteRequest — Files URIs are rejected.
     */
    private fun resolveAudioUri(activity: Activity, file: File): Uri? {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cursor = activity.contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DATA} = ?",
            arrayOf(file.absolutePath),
            null
        ) ?: return null
        return cursor.use {
            if (it.moveToFirst())
                ContentUris.withAppendedId(collection, it.getLong(0))
            else null
        }
    }

    /**
     * Requests write access to [uris] from the user.
     *
     * Android 11+ (R): shows one system dialog via createWriteRequest.
     *   The result arrives in onActivityResult — [onAlreadyGranted] is NOT called.
     * Android 10  (Q): runs [onAlreadyGranted] and catches RecoverableSecurityException
     *   if the system blocks the write; then launches the per-file dialog.
     * Android ≤ 9     : runs [onAlreadyGranted] immediately (manifest perm is enough).
     */
    private fun requestWriteAccess(
        activity: Activity,
        uris: List<Uri>,
        requestCode: Int,
        onAlreadyGranted: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createWriteRequest(activity.contentResolver, uris)
            try {
                activity.startIntentSenderForResult(
                    pi.intentSender, requestCode, null, 0, 0, 0
                )
            } catch (e: IntentSender.SendIntentException) {
                Log.e(TAG, "createWriteRequest failed", e)
                Toast.makeText(activity, "Could not request write access", Toast.LENGTH_SHORT).show()
            }
            // Result handled in onActivityResult
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            try {
                onAlreadyGranted()
            } catch (e: Exception) {
                val rse = (e as? android.app.RecoverableSecurityException)
                    ?: (e.cause as? android.app.RecoverableSecurityException)
                if (rse != null) {
                    try {
                        activity.startIntentSenderForResult(
                            rse.userAction.actionIntent.intentSender,
                            requestCode, null, 0, 0, 0
                        )
                    } catch (se: IntentSender.SendIntentException) {
                        Log.e(TAG, "RecoverableSecurityException intent failed", se)
                        Toast.makeText(activity, "Could not request write access", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "Unexpected write failure on Q", e)
                    Toast.makeText(activity, "Operation failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            onAlreadyGranted()
        }
    }

    private fun doRename(
        activity: Activity,
        uri: Uri,
        newFileName: String,
        onChanged: () -> Unit,
        dialog: Dialog?
    ) {
        val ok = try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, newFileName)
            }
            activity.contentResolver.update(uri, values, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "doRename failed", e); false
        }
        Toast.makeText(
            activity,
            if (ok) "Track renamed" else "Could not rename file",
            Toast.LENGTH_SHORT
        ).show()
        if (ok) { onChanged(); dialog?.dismiss() }
    }

    private fun doDelete(
        activity: Activity,
        uri: Uri,
        onChanged: () -> Unit,
        dialog: Dialog?
    ) {
        val ok = try {
            activity.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "doDelete failed", e); false
        }
        Toast.makeText(
            activity,
            if (ok) "Track deleted" else "Could not delete file",
            Toast.LENGTH_SHORT
        ).show()
        if (ok) { onChanged(); dialog?.dismiss() }
    }

    private fun handleStripResult(
        activity: Activity,
        ok: Boolean,
        metadataView: TextView,
        onChanged: () -> Unit
    ) {
        if (ok) {
            metadataView.text = "(metadata removed)"
            Toast.makeText(activity, "Embedded metadata removed", Toast.LENGTH_SHORT).show()
            onChanged()
        } else {
            Toast.makeText(activity, "Failed to remove metadata", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Metadata ─────────────────────────────────────────────────────────────

    private val META_KEYS = listOf(
        MediaMetadataRetriever.METADATA_KEY_TITLE       to "Title",
        MediaMetadataRetriever.METADATA_KEY_ARTIST      to "Artist",
        MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST to "Album Artist",
        MediaMetadataRetriever.METADATA_KEY_ALBUM       to "Album",
        MediaMetadataRetriever.METADATA_KEY_YEAR        to "Year",
        MediaMetadataRetriever.METADATA_KEY_GENRE       to "Genre",
        MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER to "Disc #",
        MediaMetadataRetriever.METADATA_KEY_COMPOSER    to "Composer",
        MediaMetadataRetriever.METADATA_KEY_DURATION    to "Duration (ms)",
        MediaMetadataRetriever.METADATA_KEY_BITRATE     to "Bitrate",
        MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO   to "Has Audio",
    )

    private fun readEmbeddedMetadata(file: File): Map<String, String> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val result = linkedMapOf<String, String>()
            val art = retriever.embeddedPicture
            if (art != null) result["Cover Art"] = "${art.size} bytes"
            for ((key, label) in META_KEYS) {
                val value = retriever.extractMetadata(key)
                if (!value.isNullOrBlank()) result[label] = value
            }
            result
        } catch (e: Exception) {
            emptyMap()
        } finally {
            retriever.release()
        }
    }

    /**
     * Strips all embedded tags using JAudioTagger.
     *
     * Uses the *static* AudioFileIO.delete(audioFile) which removes tag headers
     * from the file on disk. Do NOT call the instance audioFile.delete() — that
     * deletes the actual file.
     *
     * On Android 11+ this must be called after write access has been granted via
     * createWriteRequest, otherwise the FileOutputStream will be blocked.
     */
    private fun stripEmbeddedMetadata(activity: Activity, track: Track): Boolean {
        val supported = setOf("mp3", "m4a", "aac", "flac", "ogg", "wav", "aiff", "wma")
        if (track.ext.lowercase() !in supported) {
            Toast.makeText(
                activity,
                "Metadata removal not supported for .${track.ext} files",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(track.file)
            org.jaudiotagger.audio.AudioFileIO.delete(audioFile)
            true
        } catch (e: Exception) {
            Log.e(TAG, "stripEmbeddedMetadata failed", e)
            false
        }
    }
}
