package code.name.monkey.retromusic.service

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.extensions.uri
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.service.playback.Playback.PlaybackCallbacks
import code.name.monkey.retromusic.util.logE
import code.name.monkey.retromusic.util.PreferenceUtil.playbackPitch
import code.name.monkey.retromusic.util.PreferenceUtil.playbackSpeed
import code.name.monkey.retromusic.util.PreferenceUtil.isSkipSilence
import code.name.monkey.retromusic.util.PreferenceUtil.enableReplayGain
import code.name.monkey.retromusic.util.PreferenceUtil.preferAlbumGain
import code.name.monkey.retromusic.util.Taglib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

class RetroExoPlayer(context: Context) : AudioManagerPlayback(context), Player.Listener {
    private var player: ExoPlayer = ExoPlayer.Builder(context).build()
    override var callbacks: PlaybackCallbacks? = null

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * @return True if the player is ready to go, false otherwise
     */
    override var isInitialized = false
        private set

    init {
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            false
        )
        player.setSkipSilenceEnabled(isSkipSilence)
        player.addListener(this)
    }

    /**
     * @param song The song object you want to play
     * @return True if the `player` has been prepared and is ready to play, false otherwise
     */
    override fun setDataSource(
        song: Song,
        force: Boolean,
        completion: (success: Boolean) -> Unit,
    ) {
        isInitialized = false
        val mediaItem = MediaItem.fromUri(song.uri)
        try {
            Handler(Looper.getMainLooper()).post {
                player.setMediaItem(mediaItem)
                player.playbackParameters = PlaybackParameters(playbackSpeed, playbackPitch)

                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            player.removeListener(this)
                            isInitialized = true
                            initAudioEffects()
                            applyReplayGain(song)
                            completion(true)
                        }
                    }
                })
                player.prepare()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            completion(false)
        }
    }

    /**
     * Set the MediaPlayer to start when this MediaPlayer finishes playback.
     *
     * @param path The path of the file, or the http/rtsp URL of the stream you want to play
     */
    override fun setNextDataSource(path: Uri?) {}

    /**
     * Starts or resumes playback.
     */
    override fun start(): Boolean {
        super.start()
        return try {
            player.play()
            true
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Resets the MediaPlayer to its uninitialized state.
     */
    override fun stop() {
        super.stop()
        player.stop()
        isInitialized = false
    }

    /**
     * Releases resources associated with this MediaPlayer object.
     */
    override fun release() {
        stop()
        releaseAudioEffects()
        scope.cancel()
        player.release()
    }

    /**
     * Pauses playback. Call start() to resume.
     */
    override fun pause(): Boolean {
        super.pause()
        return try {
            player.pause()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    /**
     * Checks whether the MultiPlayer is playing.
     */
    override val isPlaying: Boolean
        get() = isInitialized && (player.isPlaying || player.playbackState == Player.STATE_ENDED)

    /**
     * Gets the duration of the file.
     *
     * @return The duration in milliseconds
     */
    override fun duration(): Int {
        return if (!this.isInitialized) {
            -1
        } else try {
            player.duration.toInt()
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Gets the current playback position.
     *
     * @return The current position in milliseconds
     */
    override fun position(): Int {
        return if (!this.isInitialized) {
            -1
        } else try {
            player.currentPosition.toInt()
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Gets the current playback position.
     *
     * @param whereto The offset in milliseconds from the start to seek to
     * @return The offset in milliseconds from the start to seek to
     */
    override fun seek(whereto: Int, force: Boolean): Int {
        return try {
            player.seekTo(whereto.toLong())
            whereto
        } catch (e: Exception) {
            -1
        }
    }

    override fun setVolume(vol: Float): Boolean {
        return try {
            player.volume = vol
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sets the audio session ID.
     *
     * @param sessionId The audio session ID
     */
    @OptIn(UnstableApi::class)
    override fun setAudioSessionId(sessionId: Int): Boolean {
        return try {
            player.audioSessionId = sessionId
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns the audio session ID.
     *
     * @return The current audio session ID.
     */
    override val audioSessionId: Int
        @OptIn(UnstableApi::class)
        get() = player.audioSessionId

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) {
            callbacks?.onTrackEnded()
        } else {
            callbacks?.onPlayStateChanged()
        }
    }

    

    override fun onPlayerError(error: PlaybackException) {
        logE(error)
        isInitialized = false
        releaseAudioEffects()
        player.release()
        player = ExoPlayer.Builder(context).build()
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            false
        )
        player.setSkipSilenceEnabled(isSkipSilence)
        player.addListener(this)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            callbacks?.onTrackWentToNext()
            return
        }
    }

    override fun setCrossFadeDuration(duration: Int) {}

    override fun setPlaybackSpeedPitch(speed: Float, pitch: Float) {
        player.playbackParameters = PlaybackParameters(speed, pitch)
    }

    private fun applyReplayGain(song: Song) {
        if (enableReplayGain == false) return
        if (song == Song.emptySong) return
        val preferAlbumGain = preferAlbumGain
        
        scope.launch {
            val tags =  Taglib.getAllTags(context, song)

            fun parse(value: String?, default: Float = 0f): Float {
                return value?.replace("dB", "", ignoreCase = true)
                    ?.replace("[^\\d+-.]".toRegex(), "")
                    ?.toFloatOrNull() ?: default
            }
            val trackGain = parse(tags["REPLAYGAIN_TRACK_GAIN"]?.firstOrNull())
            val albumGain = parse(tags["REPLAYGAIN_ALBUM_GAIN"]?.firstOrNull())
            val trackPeak = parse(tags["REPLAYGAIN_TRACK_PEAK"]?.firstOrNull(), 1f)
            val albumPeak = parse(tags["REPLAYGAIN_ALBUM_PEAK"]?.firstOrNull(), 1f)
        
            val adjustDB = if (preferAlbumGain == true) {
                if ( albumGain != 0f)  albumGain else trackGain
            } else {
                if (trackGain != 0f) trackGain else albumGain
            }

            val peak = if (preferAlbumGain == true) {
                if (albumPeak != 1f) albumPeak else trackPeak
            } else {
                if (trackPeak != 1f) trackPeak else albumPeak
            }
        
            val safeDB = min(adjustDB, -20f * log10(peak))
        
            val gain = 10.0f.pow(safeDB / 20f).coerceIn(0f, 1f)
            
            scope.launch(Dispatchers.Main) {
                setVolume(gain)
            }
        }
    }

    private fun applyEqualizerPreferences() {
        val prefs = context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("equalizer_enabled", false)

        if (!isEnabled) {
            setEqualizerEnabled(false)
            setBassBoostStrength(0)
            setVirtualizerStrength(0)
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            return
        }
        
        setEqualizerEnabled(isEnabled)
        for (i in 0 until (equalizer?.numberOfBands ?: 0)) {
            val level = prefs.getFloat("band_$i", 0f)
            setEqualizerBandLevel(i.toShort(), (level * 100).toInt().toShort())
        }

        val virtualizerStrength = (prefs.getFloat("virtualizer_strength", 0f) * 10).toInt().toShort()
        setVirtualizerStrength(virtualizerStrength)
        
        val bassBoostStrength = (prefs.getFloat("bass_boost_strength", 0f) * 10).toInt().toShort()
        setBassBoostStrength(bassBoostStrength)

        val amplifierStrength = (prefs.getFloat("amplifier_strength", 0f) * 10).toInt().toShort()
        setAmplifierStrength(amplifierStrength)
    }

    private fun registerPrefListener() {
        val prefs = context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun unregisterPrefListener() {
        val prefs = context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when {
            (key?.startsWith("band_") == true || key == "equalizer_enabled") -> {
                applyEqualizerPreferences()
            }
            key == "virtualizer_strength" -> {
                val value = sharedPreferences.getFloat(key, 0f)
                setVirtualizerStrength((value * 10).toInt().toShort())
            }
            key == "bass_boost_strength" -> {
                val value = sharedPreferences.getFloat(key, 0f)
                setBassBoostStrength((value * 10).toInt().toShort())
            }
            key == "amplifier_strength" -> {
                val value = sharedPreferences.getFloat(key, 0f)
                setAmplifierStrength((value * 10).toInt().toShort())
            }
        }
    }

    private fun initAudioEffects() {
        releaseAudioEffects()

        val sessionId = audioSessionId

        equalizer = Equalizer(0, sessionId).apply {
            enabled = true
        }

        bassBoost = BassBoost(0, sessionId).apply {
            enabled = true
        }

        virtualizer = Virtualizer(0, sessionId).apply {
            enabled = true
        }
        registerPrefListener()
        applyEqualizerPreferences()
    }

    private fun releaseAudioEffects() {
        unregisterPrefListener()
        
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        loudnessEnhancer?.release()

        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }

    fun getEqualizerMinBandLevel(): Short {
        return equalizer?.bandLevelRange?.get(0) ?: 0
    }

    fun getEqualizerMaxBandLevel(): Short {
        return equalizer?.bandLevelRange?.get(1) ?: 0
    }

    fun setEqualizerBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
        } catch (_: Exception) {
        }
    }

    fun setBassBoostStrength(strength: Short) {
        try {
            bassBoost?.setStrength(strength)
        } catch (_: Exception) {
        }
    }

    fun setVirtualizerStrength(strength: Short) {
        try {
            virtualizer?.setStrength(strength)
        } catch (_: Exception) {
        }
    }

    fun setAmplifierStrength(strength: Short) {
        try {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            }

            loudnessEnhancer?.enabled = true
            loudnessEnhancer?.setTargetGain(strength.toInt())
        } catch (_: Exception) {
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
    }

    companion object {
        val TAG: String = RetroExoPlayer::class.java.simpleName
    }
}
