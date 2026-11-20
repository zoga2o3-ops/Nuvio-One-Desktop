package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gives Music its own YouTube profile without a second Google sign-in.
 *
 * The desktop engine keeps one shared account list with one selected profile, so Music pins the
 * profile it should use, selects it while the Music section is open, and restores the profile that
 * was active before on exit. Liked songs still live on the YouTube account, so likes made in Music
 * are written back with [YouTubeEngine.setLike].
 */
internal object MusicAccountScope {
    private val store = DesktopStorage.store("nuvio_music_account")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var pinnedKey by mutableStateOf(store.getString("pinned_key")?.takeIf { it.isNotBlank() })
        private set
    var accounts by mutableStateOf<List<YouTubeAccountItem>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    private var restoreKey: String? = store.getString("restore_key")?.takeIf { it.isNotBlank() }
    private var entered = false

    val pinnedAccount: YouTubeAccountItem?
        get() = pinnedKey?.let { key -> accounts.firstOrNull { it.key == key } }

    /** Key of the profile Music is currently using; changes on sign-in, pin, unpin or switch. */
    val activeKey: String?
        get() = accounts.firstOrNull { it.selected }?.key

    suspend fun refreshAccounts() {
        busy = true
        runCatching { YouTubeEngine.accounts() }
            .onSuccess { accounts = it }
            .onFailure { status = it.message ?: "Could not read YouTube profiles." }
        busy = false
    }

    /** Loads the account list for the header avatar and selects the pinned Music profile. */
    suspend fun enter() {
        if (entered) return
        entered = true
        runCatching { YouTubeEngine.accounts() }.onSuccess { accounts = it }
        val pinned = pinnedKey ?: return
        runCatching { YouTubeEngine.refreshAccounts() }
        runCatching { YouTubeEngine.accounts() }.onSuccess { list ->
            accounts = list
            val target = list.firstOrNull { it.key == pinned } ?: return@onSuccess
            if (target.selected) return@onSuccess
            val previous = list.firstOrNull { it.selected }?.key
            if (previous != null && previous != pinned) {
                restoreKey = previous
                store.putString("restore_key", previous)
            }
            runCatching { YouTubeEngine.selectAccount(pinned) }
                .onSuccess { accounts = list.map { it.copy(selected = it.key == pinned) } }
                .onFailure { status = it.message ?: "Could not switch the Music profile." }
        }
    }

    /** Puts the OneTube profile back when Music closes. */
    fun exit() {
        if (!entered) return
        entered = false
        val key = restoreKey ?: return
        restoreKey = null
        store.putString("restore_key", "")
        scope.launch { runCatching { YouTubeEngine.selectAccount(key) } }
    }

    suspend fun pin(key: String) {
        val current = accounts.firstOrNull { it.selected }
        if (current != null && current.key != key) {
            restoreKey = current.key
            store.putString("restore_key", current.key)
        }
        pinnedKey = key
        store.putString("pinned_key", key)
        runCatching { YouTubeEngine.selectAccount(key) }
            .onSuccess { accounts = accounts.map { it.copy(selected = it.key == key) } }
            .onFailure { status = it.message ?: "Could not switch the Music profile." }
    }

    fun unpin() {
        pinnedKey = null
        store.putString("pinned_key", "")
        status = null
        val key = restoreKey
        if (key != null) {
            restoreKey = null
            store.putString("restore_key", "")
            scope.launch {
                runCatching { YouTubeEngine.selectAccount(key) }
                runCatching { YouTubeEngine.accounts() }.onSuccess { accounts = it }
            }
        }
    }
}
