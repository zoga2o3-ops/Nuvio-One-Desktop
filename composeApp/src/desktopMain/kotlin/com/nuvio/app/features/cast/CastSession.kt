package com.nuvio.app.features.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What is being cast right now, for the player panel and the bar at the bottom of the app. */
internal data class CastState(
    val device: CastDevice,
    val title: String,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val connecting: Boolean = true,
    val error: String? = null,
    /** The player surface that started it; null once that player was closed (the app bar takes over). */
    val owner: Any? = null,
)

/** The one cast in progress (one device at a time). */
internal object CastSession {
    private val _state = MutableStateFlow<CastState?>(null)
    val state: StateFlow<CastState?> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var player: CastPlayer? = null
    private var job: Job? = null

    fun start(device: CastDevice, media: CastMedia, startMs: Long, owner: Any?) {
        end()
        _state.value = CastState(device = device, title = media.title, positionMs = startMs, owner = owner)
        job = scope.launch {
            try {
                val created = when (device.kind) {
                    CastKind.CHROMECAST -> ChromecastPlayer(device)
                    CastKind.DLNA -> DlnaPlayer(device)
                }
                player = created
                val playUrl = if (CastRelay.needed(device, media)) CastRelay.wrap(media.url, media.headers, device) else media.url
                created.load(media, playUrl, startMs)
                _state.update { it?.copy(connecting = false, playing = true) }
                var failures = 0
                while (isActive) {
                    delay(1000)
                    val status = runCatching { created.status() }
                        .onFailure { if (++failures >= 5) throw it }
                        .getOrNull()
                    if (status != null) {
                        failures = 0
                        _state.update { it?.copy(positionMs = status.positionMs, durationMs = status.durationMs, playing = status.playing || status.buffering) }
                        if (status.ended) {
                            _state.update { it?.copy(playing = false) }
                        }
                    }
                }
            } catch (e: Exception) {
                if (_state.value != null) {
                    _state.update { it?.copy(connecting = false, playing = false, error = e.message ?: "Casting failed.") }
                }
            }
        }
    }

    fun togglePause() {
        val current = _state.value ?: return
        _state.update { it?.copy(playing = !current.playing) }
        scope.launch { runCatching { if (current.playing) player?.pause() else player?.play() } }
    }

    fun seekTo(positionMs: Long) {
        _state.update { it?.copy(positionMs = positionMs) }
        scope.launch { runCatching { player?.seek(positionMs) } }
    }

    /** Stops the TV and ends the session; the position it had reached, to carry on here. */
    fun stop(): Long {
        val position = _state.value?.positionMs ?: 0L
        end()
        return position
    }

    /** The player that started the cast was closed: keep casting, controlled from the app bar. */
    fun release(owner: Any) {
        _state.update { if (it?.owner === owner) it.copy(owner = null) else it }
    }

    private fun end() {
        job?.cancel()
        job = null
        val old = player
        player = null
        _state.value = null
        if (old != null) {
            scope.launch {
                runCatching { old.stop() }
                runCatching { old.close() }
                CastRelay.clear()
            }
        }
    }
}
