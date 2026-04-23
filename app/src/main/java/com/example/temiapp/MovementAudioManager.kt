package com.example.temiapp

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * 統一管理 Temi 移動時的柔和背景音。
 * 規則：
 * 1. 只要進入移動狀態就播放循環背景音。
 * 2. 一旦開始 TTS / 廣播講話，立即暫停背景音。
 * 3. TTS 結束後，若仍在移動，則自動恢復背景音。
 * 4. Hey Temi 觸發後先停止背景音，待語音互動結束再視狀態恢復。
 */
object MovementAudioManager {

    private const val TAG = "MovementAudioManager"
    private const val DEFAULT_VOLUME = 0.45f

    private val lock = Any()

    private var appContext: Context? = null
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var movementActive = false

    @Volatile
    private var speechActive = false

    @Volatile
    private var wakeInteractionActive = false

    fun onMovementStarted(context: Context) {
        synchronized(lock) {
            appContext = context.applicationContext
            movementActive = true
            refreshPlaybackLocked()
        }
    }

    fun onMovementStopped() {
        synchronized(lock) {
            movementActive = false
            pausePlaybackLocked(resetToStart = true)
        }
    }

    fun onSpeechStart() {
        synchronized(lock) {
            speechActive = true
            pausePlaybackLocked(resetToStart = false)
        }
    }

    fun onSpeechEnd(context: Context? = null) {
        synchronized(lock) {
            context?.let { appContext = it.applicationContext }
            speechActive = false
            refreshPlaybackLocked()
        }
    }

    fun onWakeInteractionStart() {
        synchronized(lock) {
            wakeInteractionActive = true
            pausePlaybackLocked(resetToStart = false)
        }
    }

    fun onWakeInteractionEnd(context: Context? = null) {
        synchronized(lock) {
            context?.let { appContext = it.applicationContext }
            wakeInteractionActive = false
            refreshPlaybackLocked()
        }
    }

    fun stopAndReset() {
        synchronized(lock) {
            movementActive = false
            speechActive = false
            wakeInteractionActive = false
            releasePlayerLocked()
        }
    }

    private fun refreshPlaybackLocked() {
        if (movementActive && !speechActive && !wakeInteractionActive) {
            startPlaybackLocked()
        } else {
            pausePlaybackLocked(resetToStart = false)
        }
    }

    private fun startPlaybackLocked() {
        val context = appContext ?: return
        val player = mediaPlayer ?: createPlayer(context)?.also { mediaPlayer = it } ?: return

        runCatching {
            if (!player.isPlaying) {
                player.start()
            }
        }.onFailure {
            Log.w(TAG, "播放移動背景音失敗", it)
            releasePlayerLocked()
        }
    }

    private fun pausePlaybackLocked(resetToStart: Boolean) {
        val player = mediaPlayer ?: return

        runCatching {
            if (player.isPlaying) {
                player.pause()
            }
            if (resetToStart) {
                player.seekTo(0)
            }
        }.onFailure {
            Log.w(TAG, "暫停移動背景音失敗", it)
            releasePlayerLocked()
        }
    }

    private fun createPlayer(context: Context): MediaPlayer? {
        return runCatching {
            val bgmId = context.resources.getIdentifier("bgm", "raw", context.packageName)
            val fallbackId = context.resources.getIdentifier("nursing", "raw", context.packageName)
            val resId = when {
                bgmId != 0 -> bgmId
                fallbackId != 0 -> fallbackId
                else -> 0
            }
            if (resId == 0) return null

            MediaPlayer.create(context, resId).apply {
                isLooping = true
                setVolume(DEFAULT_VOLUME, DEFAULT_VOLUME)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            }
        }.getOrElse {
            Log.e(TAG, "建立移動背景音播放器失敗", it)
            null
        }
    }

    private fun releasePlayerLocked() {
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }
}
