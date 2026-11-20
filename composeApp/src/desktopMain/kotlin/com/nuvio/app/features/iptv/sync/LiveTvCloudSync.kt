package com.nuvio.app.features.iptv.sync

import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.network.ServerConfigurationRepository
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.iptv.IptvSourceStore
import com.nuvio.app.features.iptv.sync.LiveTvMerge.str
import io.github.jan.supabase.auth.auth
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class CloudSyncState(
    /** Only on the official Nuvio server: that's the sign-in the sync service checks. */
    val available: Boolean = false,
    val signedIn: Boolean = false,
    val email: String? = null,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val lastSyncMs: Long = 0L,
    val prompt: CloudPrompt? = null,
)

internal enum class CloudPrompt { BackUp, Choose }

internal enum class CloudChoice { UseSaved, Combine, Later }

/**
 * Keeps the Live TV setup the same on every device signed in to the same Nuvio account: the
 * desktop port of the TV app's LiveTvCloudSync. The Nuvio access token goes to the same small
 * service the TVs use (the livetv-cloud Worker), which checks with Nuvio whose it is and keeps one
 * copy per account. A device never overwrites a copy it hasn't seen: a save from an out-of-date
 * copy is refused (409) and the next round combines.
 */
@OptIn(FlowPreview::class)
internal object LiveTvCloudSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = DesktopStorage.store("livetv_cloud_sync")
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val available: Boolean
        get() {
            val config = ServerConfigurationRepository.active.value
            return !config.isCustom &&
                runCatching { URI(config.backendUrl).host }.getOrNull().equals("api.nuvio.tv", ignoreCase = true)
        }

    private val _state = MutableStateFlow(CloudSyncState(lastSyncMs = lastSync()))
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()

    @Volatile private var started = false
    @Volatile private var userId: String? = null
    @Volatile private var applying = false
    @Volatile private var driveWasConnected = false

    private val deviceId: String
        get() = store.getString(KEY_DEVICE_ID) ?: UUID.randomUUID().toString().also { store.putString(KEY_DEVICE_ID, it) }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        _state.value = _state.value.copy(available = available)
        if (!available) return
        LiveTvDriveSync.importListener = { scope.launch { runImport() } }
        scope.launch { AuthRepository.state.collect { onAuthState(it) } }
        scope.launch {
            LiveTvSyncLocal.changes.drop(1).debounce(8_000).collect {
                if (!applying && isOn()) syncIfChanged()
            }
        }
        scope.launch {
            while (isActive) {
                delay(60 * 60_000L)
                if (isOn()) syncNow()
            }
        }
    }

    private suspend fun onAuthState(auth: AuthState) {
        when {
            auth is AuthState.Authenticated && !auth.isAnonymous -> {
                userId = auth.userId
                val on = isEnabled(auth.userId)
                _state.value = _state.value.copy(signedIn = true, email = auth.email, enabled = on)
                mutex.withLock {
                    if (on) reconcileAndReport(auth.userId) else decideFirstTime(auth.userId, explicit = false)
                }
            }
            auth is AuthState.Loading -> Unit
            else -> {
                userId = null
                _state.value = _state.value.copy(signedIn = false, email = null, enabled = false, prompt = null)
            }
        }
    }

    // ------------------------------------------------------------------ settings actions

    fun syncNow() {
        scope.launch {
            mutex.withLock {
                val uid = userId ?: return@withLock
                if (isEnabled(uid)) reconcileAndReport(uid, doneMessage = "Up to date.")
            }
        }
    }

    fun turnOn() {
        scope.launch {
            mutex.withLock {
                val uid = userId ?: return@withLock
                store.putBoolean(KEY_DECLINED + uid, false)
                _state.value = _state.value.copy(prompt = null, busy = true, message = null)
                decideFirstTime(uid, explicit = true)
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun turnOff() {
        val uid = userId ?: return
        setEnabled(uid, false)
        store.putBoolean(KEY_DECLINED + uid, true)
        _state.value = _state.value.copy(enabled = false, prompt = null, message = "Off. Your saved copy stays in your Nuvio account.")
    }

    fun deleteCloudCopy() {
        scope.launch {
            mutex.withLock {
                val uid = userId ?: return@withLock
                _state.value = _state.value.copy(busy = true, message = null)
                val message = runCatching {
                    val reply = call("DELETE", "/v1/setup")
                    if (reply.code == 200) {
                        setEnabled(uid, false)
                        store.putBoolean(KEY_DECLINED + uid, true)
                        Files.deleteIfExists(baseFile())
                        "Deleted your saved Live TV setup from the cloud. This computer keeps its own setup."
                    } else failText(reply.code)
                }.getOrElse { NETWORK_TEXT }
                _state.value = _state.value.copy(busy = false, enabled = isEnabled(uid), message = message)
            }
        }
    }

    fun restorePrevious() {
        scope.launch {
            mutex.withLock {
                val uid = userId ?: return@withLock
                _state.value = _state.value.copy(busy = true, message = null)
                val message = runCatching {
                    val reply = call("GET", "/v1/setup/previous")
                    val data = reply.json?.get("data") as? JsonObject
                    when {
                        reply.code == 404 -> "There is no earlier copy yet."
                        reply.code != 200 || data == null -> failText(reply.code)
                        else -> {
                            apply(data)
                            when (val outcome = put(reply.json.int("currentRevision") ?: 0, data)) {
                                is Put.Saved -> { saved(uid, data, emptySet()); "Restored the earlier copy." }
                                else -> putText(outcome)
                            }
                        }
                    }
                }.getOrElse { NETWORK_TEXT }
                _state.value = _state.value.copy(busy = false, lastSyncMs = lastSync(), message = message)
            }
        }
    }

    /** Brings the Google Drive backup into the Nuvio account (signing in to Drive first if needed). */
    fun importFromDrive() {
        scope.launch {
            if (userId == null) return@launch
            driveWasConnected = LiveTvDriveSync.state.value.connected
            if (driveWasConnected) runImport()
            else {
                _state.value = _state.value.copy(message = null)
                LiveTvDriveSync.beginSignIn(forImport = true)
            }
        }
    }

    private suspend fun runImport() {
        mutex.withLock {
            val uid = userId ?: return@withLock
            _state.value = _state.value.copy(busy = true, message = null)
            val message = runCatching {
                val driveData = LiveTvDriveSync.backupData()
                if (!driveWasConnected) LiveTvDriveSync.disconnect()
                when {
                    driveData == null -> "Couldn't find a Live TV backup in your Google Drive."
                    !LiveTvSyncLocal.syncHasSources(driveData) -> "The backup in your Google Drive has no playlists or guides to bring over."
                    else -> {
                        val local = LiveTvSyncLocal.exportForSync()
                        val removedHere = LiveTvSyncLocal.removedSourceIds()
                        val imported = if (LiveTvSyncLocal.syncHasSources(local)) {
                            LiveTvMerge.merge(null, local, driveData, false, removedHere)
                        } else driveData
                        if (imported.toString() != local.toString()) apply(imported)
                        store.putBoolean(KEY_DECLINED + uid, false)
                        setEnabled(uid, true)
                        val note = reconcile(uid)
                        if (note == null || note == UPDATED_TEXT) "Brought your Live TV setup over from Google Drive and saved it to your Nuvio account."
                        else note
                    }
                }
            }.getOrElse { NETWORK_TEXT }
            _state.value = _state.value.copy(busy = false, enabled = isEnabled(uid), lastSyncMs = lastSync(), message = message)
        }
    }

    // ------------------------------------------------------------------ first-time questions

    fun answerBackUp(yes: Boolean) {
        scope.launch {
            mutex.withLock {
                val uid = userId ?: return@withLock
                _state.value = _state.value.copy(prompt = null)
                if (!yes) {
                    store.putBoolean(KEY_DECLINED + uid, true)
                    return@withLock
                }
                _state.value = _state.value.copy(busy = true, message = null)
                decideFirstTime(uid, explicit = true)
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun answerChoose(choice: CloudChoice) {
        scope.launch {
            mutex.withLock {
                val uid = userId ?: return@withLock
                _state.value = _state.value.copy(prompt = null)
                if (choice == CloudChoice.Later) {
                    store.putBoolean(KEY_DECLINED + uid, true)
                    return@withLock
                }
                _state.value = _state.value.copy(busy = true, message = null)
                val message = runCatching {
                    val remote = fetch() as? Remote.Found ?: return@runCatching "Couldn't reach your saved setup. Try again."
                    val removedHere = LiveTvSyncLocal.removedSourceIds()
                    if (choice == CloudChoice.UseSaved) {
                        apply(remote.data)
                        saved(uid, remote.data, emptySet())
                        setEnabled(uid, true)
                        "Using your saved Live TV setup."
                    } else {
                        val local = LiveTvSyncLocal.exportForSync()
                        // This device's favourites, hidden groups and order win the first combine.
                        val merged = LiveTvMerge.merge(null, local, remote.data, true, removedHere)
                        if (merged.toString() != local.toString()) apply(merged)
                        if (merged.toString() == remote.data.toString()) {
                            saved(uid, remote.data, emptySet())
                            setEnabled(uid, true)
                            "Combined with your saved setup."
                        } else when (val result = put(remote.revision, merged)) {
                            is Put.Saved -> {
                                saved(uid, merged, removedHere)
                                setEnabled(uid, true)
                                "Combined with your saved setup."
                            }
                            else -> putText(result)
                        }
                    }
                }.getOrElse { NETWORK_TEXT }
                _state.value = _state.value.copy(busy = false, enabled = isEnabled(uid), lastSyncMs = lastSync(), message = message)
            }
        }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    // ------------------------------------------------------------------ deciding and syncing

    private fun isEnabled(uid: String) = store.getBoolean(KEY_ENABLED + uid) ?: false
    private fun isOn(): Boolean = userId?.let { isEnabled(it) } == true

    private fun setEnabled(uid: String, on: Boolean) {
        store.putBoolean(KEY_ENABLED + uid, on)
        if (userId == uid) _state.value = _state.value.copy(enabled = on)
    }

    private suspend fun decideFirstTime(uid: String, explicit: Boolean) {
        if (!explicit && store.getBoolean(KEY_DECLINED + uid) == true) return
        runCatching {
            val local = LiveTvSyncLocal.exportForSync()
            val hasLocal = LiveTvSyncLocal.syncHasSources(local)
            val remote = fetch()
            when {
                remote is Remote.Failed -> {
                    if (explicit) _state.value = _state.value.copy(message = failText(remote.code))
                }
                remote is Remote.Found && LiveTvSyncLocal.syncHasSources(remote.data) -> {
                    if (!hasLocal) {
                        apply(remote.data)
                        saved(uid, remote.data, emptySet())
                        setEnabled(uid, true)
                        _state.value = _state.value.copy(lastSyncMs = lastSync(), message = "Restored your Live TV setup from your Nuvio account.")
                    } else {
                        _state.value = _state.value.copy(prompt = CloudPrompt.Choose)
                    }
                }
                else -> {
                    val savedRevision = (remote as? Remote.Found)?.revision ?: 0
                    when {
                        !hasLocal -> if (explicit) setEnabled(uid, true)
                        explicit -> backUpFirst(uid, savedRevision, local)
                        else -> _state.value = _state.value.copy(prompt = CloudPrompt.BackUp)
                    }
                }
            }
        }.onFailure {
            if (explicit) _state.value = _state.value.copy(message = NETWORK_TEXT)
        }
    }

    private suspend fun backUpFirst(uid: String, baseRevision: Int, local: JsonObject) {
        val removedHere = LiveTvSyncLocal.removedSourceIds()
        when (val result = put(baseRevision, local)) {
            is Put.Saved -> {
                saved(uid, local, removedHere)
                setEnabled(uid, true)
                _state.value = _state.value.copy(lastSyncMs = lastSync(), message = "Your Live TV setup is saved to your Nuvio account.")
            }
            else -> _state.value = _state.value.copy(message = putText(result))
        }
    }

    private suspend fun syncIfChanged() {
        if (sha(LiveTvSyncLocal.exportForSync().toString()) == store.getString(KEY_LOCAL_HASH)) return
        mutex.withLock {
            val uid = userId ?: return@withLock
            if (isEnabled(uid)) reconcileAndReport(uid)
        }
    }

    private suspend fun reconcileAndReport(uid: String, doneMessage: String? = null) {
        _state.value = _state.value.copy(busy = true)
        val result = runCatching { reconcile(uid) }
        _state.value = _state.value.copy(
            busy = false,
            lastSyncMs = lastSync(),
            message = result.fold(onSuccess = { it ?: doneMessage }, onFailure = { NETWORK_TEXT }),
        )
    }

    private suspend fun reconcile(uid: String): String? {
        repeat(3) {
            val remote = fetch()
            val local = LiveTvSyncLocal.exportForSync()
            val removedHere = LiveTvSyncLocal.removedSourceIds()
            when {
                remote is Remote.Failed -> return failText(remote.code)
                remote is Remote.Found && LiveTvSyncLocal.syncHasSources(remote.data) -> {
                    val merged = LiveTvMerge.merge(readBase(uid), local, remote.data, true, removedHere)
                    val changedHere = merged.toString() != local.toString() && apply(merged)
                    val news = if (changedHere) UPDATED_TEXT else null
                    // Same as the saved copy once this desktop's own way of writing it is set aside.
                    val sameAsSaved = merged.toString() == remote.data.toString() ||
                        LiveTvSyncLocal.exportForSync().toString() == remote.data.toString()
                    if (sameAsSaved || !LiveTvSyncLocal.syncHasSources(merged)) {
                        saved(uid, remote.data, emptySet())
                        return news
                    }
                    when (val result = put(remote.revision, merged)) {
                        is Put.Saved -> { saved(uid, merged, removedHere); return news }
                        Put.Conflict -> Unit
                        else -> return putText(result)
                    }
                }
                else -> {
                    if (!LiveTvSyncLocal.syncHasSources(local)) return null
                    val savedRevision = (remote as? Remote.Found)?.revision ?: 0
                    when (val result = put(savedRevision, local)) {
                        is Put.Saved -> { saved(uid, local, removedHere); return null }
                        Put.Conflict -> Unit
                        else -> return putText(result)
                    }
                }
            }
        }
        return "Couldn't finish syncing. Will try again."
    }

    /** Writes a shared copy here without it being saved straight back. True if anything changed. */
    private fun apply(data: JsonObject): Boolean {
        applying = true
        try {
            return LiveTvSyncLocal.importFromSync(data)
        } finally {
            applying = false
        }
    }

    private fun saved(uid: String, data: JsonObject, removedHere: Set<String>) {
        runCatching {
            Files.createDirectories(baseFile().parent)
            Files.writeString(baseFile(), data.toString())
            IptvSourceStore.setOwnerOnly(baseFile())
        }
        LiveTvSyncLocal.rememberShared(data)
        store.putString(KEY_BASE_USER, uid)
        store.putString(KEY_LAST_SYNC, System.currentTimeMillis().toString())
        store.putString(KEY_LOCAL_HASH, sha(LiveTvSyncLocal.exportForSync().toString()))
        if (removedHere.isNotEmpty()) LiveTvSyncLocal.forgetRemovedSources(removedHere)
    }

    private fun baseFile() = IptvSourceStore.dataDir().resolve("livetv-cloud-base.json")

    private fun readBase(uid: String): JsonObject? {
        if (store.getString(KEY_BASE_USER) != uid) return null
        return runCatching { if (Files.isRegularFile(baseFile())) LiveTvMerge.parse(Files.readString(baseFile())) else null }.getOrNull()
    }

    private fun lastSync() = store.getString(KEY_LAST_SYNC)?.toLongOrNull() ?: 0L

    // ------------------------------------------------------------------ the service

    private sealed interface Remote {
        data object None : Remote
        data class Found(val revision: Int, val data: JsonObject) : Remote
        data class Failed(val code: Int) : Remote
    }

    private sealed interface Put {
        data class Saved(val revision: Int) : Put
        data object Conflict : Put
        data object TooBig : Put
        data object Limit : Put
        data class Failed(val code: Int) : Put
    }

    private class Reply(val code: Int, val json: JsonObject?)

    private suspend fun fetch(): Remote {
        val reply = call("GET", "/v1/setup")
        val data = reply.json?.get("data") as? JsonObject
        return when {
            reply.code == 200 && data != null -> Remote.Found(reply.json.int("revision") ?: 0, data)
            reply.code == 404 -> Remote.None
            else -> Remote.Failed(reply.code)
        }
    }

    private suspend fun put(baseRevision: Int, data: JsonObject): Put {
        val body = buildJsonObject {
            put("baseRevision", baseRevision)
            put("device", LiveTvDriveSync.DEVICE_NAME)
            put("deviceId", deviceId)
            put("data", data)
        }
        val reply = call("PUT", "/v1/setup", body)
        return when (reply.code) {
            200 -> Put.Saved(reply.json.int("revision") ?: (baseRevision + 1))
            409 -> Put.Conflict
            413 -> Put.TooBig
            429 -> Put.Limit
            else -> Put.Failed(reply.code)
        }
    }

    /** Calls the service with the Nuvio sign-in; on 401 refreshes the session once and retries. */
    private suspend fun call(method: String, path: String, body: JsonObject? = null): Reply {
        var reply = callOnce(method, path, body)
        if (reply.code == 401 && runCatching { SupabaseProvider.client.auth.refreshCurrentSession() }.isSuccess) {
            reply = callOnce(method, path, body)
        }
        return reply
    }

    private suspend fun callOnce(method: String, path: String, body: JsonObject?): Reply = withContext(Dispatchers.IO) {
        val token = SupabaseProvider.client.auth.currentAccessTokenOrNull()?.takeIf { it.isNotBlank() }
            ?: return@withContext Reply(401, null)
        val request = Request.Builder()
            .url(CLOUD_URL + path)
            .header("Authorization", "Bearer $token")
            .header("apikey", ServerConfigurationRepository.active.value.publishableKey)
            .method(method, body?.toString()?.toRequestBody(jsonType))
            .build()
        http.newCall(request).execute().use { response ->
            Reply(response.code, LiveTvMerge.parse(response.body?.string().orEmpty()))
        }
    }

    private fun JsonObject?.int(key: String): Int? = (this?.get(key) as? JsonPrimitive)?.intOrNull

    private fun failText(code: Int) = when (code) {
        401 -> "Sign in to Nuvio again to sync."
        else -> NETWORK_TEXT
    }

    private fun putText(result: Put) = when (result) {
        Put.Conflict -> "Your saved setup changed on another device. Try again."
        Put.TooBig -> "Your Live TV setup is too big to save to the cloud."
        Put.Limit -> "Too many saves today. Syncing continues tomorrow."
        is Put.Failed -> failText(result.code)
        is Put.Saved -> ""
    }

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /** The same service the TV app uses (its BuildConfig.LIVETV_SYNC_URL default). */
    private const val CLOUD_URL = "https://nuvioone-sync.nuviotv-sync.workers.dev"
    private const val UPDATED_TEXT = "Updated from your other device."
    private const val NETWORK_TEXT = "Couldn't reach sync. Will try again."
    private const val KEY_ENABLED = "enabled_"
    private const val KEY_DECLINED = "declined_"
    private const val KEY_BASE_USER = "base_user"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_LOCAL_HASH = "local_hash"
    private const val KEY_DEVICE_ID = "device_id"
}
