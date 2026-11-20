package com.nuvio.app.features.iptv.sync

import com.nuvio.app.core.storage.DesktopSecretStore
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.iptv.IptvSourceStore
import com.nuvio.app.features.iptv.sync.LiveTvMerge.str
import java.net.URLEncoder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class DriveSyncState(
    val available: Boolean = false,
    val connected: Boolean = false,
    val email: String? = null,
    val autoSync: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val lastSyncMs: Long = 0L,
    /** Set while signing in: show this code and address. */
    val signIn: DriveSignInPrompt? = null,
)

internal data class DriveSignInPrompt(val userCode: String, val url: String, val qrUrl: String, val expiresAtMs: Long)

/**
 * Live TV sync through the person's own Google Drive: the desktop port of the TV app's
 * LiveTvDriveSync. Same Google project, file and format as the TVs ("Nuvio Live TV backup.json" in
 * Drive's hidden app folder), so a desktop and a TV share one backup. Sign-in is Google's
 * "TVs and limited input devices" flow: a code to approve on the phone or in a browser.
 */
@OptIn(FlowPreview::class)
internal object LiveTvDriveSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = DesktopStorage.store("livetv_drive_sync")
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val clientId = LiveTvDriveConfig.CLIENT_ID
    private val clientSecret = LiveTvDriveConfig.CLIENT_SECRET

    init {
        // A refresh token belongs to the Google client that issued it: when the client changes,
        // ask to reconnect instead of failing every sync.
        if (store.getString(KEY_CLIENT_ID) != clientId) {
            store.removeAll(listOf(KEY_REFRESH, KEY_EMAIL, KEY_FILE_ID, KEY_LAST_HASH, KEY_MODE))
            store.putString(KEY_CLIENT_ID, clientId)
        }
    }

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<DriveSyncState> = _state.asStateFlow()

    private var started = false
    private var signInJob: Job? = null
    private var accessToken: String? = null
    private var accessTokenExpiresAt = 0L
    @Volatile private var applying = false
    @Volatile private var importOnly = false
    /** Called when an import-only sign-in finishes: the Nuvio account sync takes the backup from here. */
    @Volatile var importListener: (() -> Unit)? = null
    @Volatile private var emptyRefused = false

    private val deviceId: String
        get() = store.getString(KEY_DEVICE_ID) ?: UUID.randomUUID().toString().also { store.putString(KEY_DEVICE_ID, it) }

    private fun refreshToken(): String? =
        store.getString(KEY_REFRESH)?.let { runCatching { DesktopSecretStore.unprotect(it) }.getOrNull() }

    private fun loadState() = DriveSyncState(
        available = clientId.isNotBlank() && clientSecret.isNotBlank(),
        connected = store.getString(KEY_REFRESH) != null,
        email = store.getString(KEY_EMAIL),
        autoSync = store.getBoolean(KEY_AUTO) ?: true,
        lastSyncMs = store.getString(KEY_LAST_SYNC)?.toLongOrNull() ?: 0L,
    )

    /** Starts automatic sync: check Drive for a newer setup, then back up changes made here. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            if (_state.value.connected && _state.value.autoSync) pull(force = false)
        }
        scope.launch {
            LiveTvSyncLocal.changes.drop(1).debounce(8_000).collect {
                val s = _state.value
                if (s.connected && s.autoSync && !applying) push(force = false)
            }
        }
    }

    // ------------------------------------------------------------------ sign-in

    fun beginSignIn(forImport: Boolean = false) {
        if (!_state.value.available) return
        importOnly = forImport
        signInJob?.cancel()
        signInJob = scope.launch {
            _state.value = _state.value.copy(busy = true, message = null, signIn = null)
            var codes = JsonObject(emptyMap())
            var mode = MODE_APPDATA
            for ((scopes, m) in SCOPE_ATTEMPTS) {
                codes = runCatching {
                    postForm("https://oauth2.googleapis.com/device/code", mapOf("client_id" to clientId, "scope" to scopes))
                }.getOrElse {
                    _state.value = _state.value.copy(busy = false, message = "Couldn't reach Google. Try again.")
                    return@launch
                }
                mode = m
                if (codes.str("error") != "invalid_scope") break
            }
            val deviceCode = codes.str("device_code").orEmpty()
            val userCode = codes.str("user_code").orEmpty()
            if (deviceCode.isBlank() || userCode.isBlank()) {
                _state.value = _state.value.copy(busy = false, message = googleError(codes))
                return@launch
            }
            val url = codes.str("verification_url").orEmpty().ifBlank { "https://www.google.com/device" }
            // Google's device page takes ?user_code= and opens with the code filled in.
            val qrUrl = codes.str("verification_uri_complete").orEmpty().ifBlank {
                url + (if ('?' in url) "&" else "?") + "user_code=" + userCode
            }
            val expiresAt = System.currentTimeMillis() + (codes.long("expires_in") ?: 1800L) * 1000
            var interval = (codes.long("interval") ?: 5L).coerceAtLeast(5L)
            _state.value = _state.value.copy(busy = false, signIn = DriveSignInPrompt(userCode, url, qrUrl, expiresAt))

            while (System.currentTimeMillis() < expiresAt) {
                delay(interval * 1000)
                val token = runCatching {
                    postForm(
                        "https://oauth2.googleapis.com/token",
                        mapOf(
                            "client_id" to clientId,
                            "client_secret" to clientSecret,
                            "device_code" to deviceCode,
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        ),
                    )
                }.getOrNull() ?: continue
                when (token.str("error").orEmpty()) {
                    "" -> {
                        store.putString(KEY_MODE, mode)
                        onSignedIn(token)
                        return@launch
                    }
                    "authorization_pending" -> Unit
                    "slow_down" -> interval += 5
                    "access_denied" -> {
                        _state.value = _state.value.copy(signIn = null, message = "Sign-in was declined.")
                        return@launch
                    }
                    else -> {
                        _state.value = _state.value.copy(signIn = null, message = googleError(token))
                        return@launch
                    }
                }
            }
            _state.value = _state.value.copy(signIn = null, message = "The code expired. Try again.")
        }
    }

    fun cancelSignIn() {
        importOnly = false
        signInJob?.cancel()
        _state.value = _state.value.copy(signIn = null, busy = false)
    }

    private suspend fun onSignedIn(token: JsonObject) {
        val refresh = token.str("refresh_token").orEmpty()
        if (refresh.isBlank()) {
            _state.value = _state.value.copy(signIn = null, message = "Google didn't return a sign-in. Try again.")
            return
        }
        accessToken = token.str("access_token")
        accessTokenExpiresAt = System.currentTimeMillis() + (token.long("expires_in") ?: 3600L) * 1000
        val email = emailFromIdToken(token.str("id_token").orEmpty())
        store.putString(KEY_REFRESH, DesktopSecretStore.protect(refresh))
        store.putString(KEY_EMAIL, email)
        _state.value = _state.value.copy(connected = true, email = email, signIn = null, message = null)
        if (importOnly) {
            importOnly = false
            importListener?.invoke()
            return
        }
        // First connection: use the Drive copy if there is one, otherwise back this desktop up.
        if (!pull(force = true, onlyIfExists = true)) push(force = true)
    }

    fun disconnect() {
        val refresh = refreshToken()
        store.removeAll(listOf(KEY_REFRESH, KEY_EMAIL, KEY_FILE_ID, KEY_LAST_HASH, KEY_MODE))
        runCatching { Files.deleteIfExists(baseFile()) }
        accessToken = null
        _state.value = _state.value.copy(connected = false, email = null, message = "Disconnected. Your backup stays in your Google Drive.")
        if (refresh != null) scope.launch {
            runCatching { postForm("https://oauth2.googleapis.com/revoke", mapOf("token" to refresh)) }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        store.putBoolean(KEY_AUTO, enabled)
        _state.value = _state.value.copy(autoSync = enabled)
    }

    fun backUpNow() { scope.launch { push(force = true) } }
    fun restoreNow() { scope.launch { pull(force = true) } }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    // ------------------------------------------------------------------ backup / restore

    private suspend fun push(force: Boolean) = mutex.withLock {
        val token = token() ?: return@withLock
        emptyRefused = false
        _state.value = _state.value.copy(busy = true, message = if (force) "Backing up…" else null)
        val result = runCatching {
            var data = LiveTvSyncLocal.exportForSync()
            val removedHere = LiveTvSyncLocal.removedSourceIds()
            if (!force && sha(data.toString()) == store.getString(KEY_LAST_HASH)) return@runCatching false
            // Another device saved since we last synced: combine its changes with ours first.
            remoteCopy(token)?.let { remote ->
                val remoteData = remote["data"] as? JsonObject
                val base = readBase()
                if (remoteData != null && remoteData.toString() != base?.toString()) {
                    val merged = LiveTvMerge.merge(base, data, remoteData, localWinsWithoutBase = true, removedHere = removedHere)
                    if (merged.toString() != data.toString()) applyLocally(merged)
                    data = merged
                }
            }
            // Never write an empty setup over the backup.
            if (!LiveTvSyncLocal.syncHasSources(data)) {
                if (force) emptyRefused = true
                return@runCatching false
            }
            val now = System.currentTimeMillis()
            val body = buildJsonObject {
                put("format", 1)
                put("savedAt", now)
                put("deviceId", deviceId)
                put("device", DEVICE_NAME)
                put("data", data)
            }.toString()
            val fileId = fileId(token)
            if (fileId == null) {
                val meta = buildJsonObject {
                    put("name", FILE_NAME)
                    if (isAppDataMode) put("parents", JsonArray(listOf(JsonPrimitive("appDataFolder"))))
                }
                val multipart = MultipartBody.Builder().setType("multipart/related".toMediaType())
                    .addPart(meta.toString().toRequestBody(JSON))
                    .addPart(body.toRequestBody(JSON))
                    .build()
                val created = execute(
                    Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
                        .header("Authorization", "Bearer $token")
                        .post(multipart)
                        .build(),
                )
                LiveTvMerge.parse(created)?.str("id")?.let { store.putString(KEY_FILE_ID, it) }
            } else {
                execute(
                    Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                        .header("Authorization", "Bearer $token")
                        .patch(body.toRequestBody(JSON))
                        .build(),
                )
            }
            store.putString(KEY_LAST_HASH, sha(LiveTvSyncLocal.exportForSync().toString()))
            store.putString(KEY_LAST_SYNC, now.toString())
            writeBase(data)
            LiveTvSyncLocal.rememberShared(data)
            LiveTvSyncLocal.forgetRemovedSources(removedHere)
            true
        }
        _state.value = _state.value.copy(
            busy = false,
            lastSyncMs = store.getString(KEY_LAST_SYNC)?.toLongOrNull() ?: 0L,
            message = when {
                result.isFailure -> "Backup failed: ${result.exceptionOrNull()?.message ?: "network error"}"
                emptyRefused -> "Nothing backed up: add a playlist or guide first. Your copy in Google Drive was left as it was."
                force -> "Backed up to Google Drive."
                else -> _state.value.message
            },
        )
    }

    /** Applies the Drive copy if it's new to this desktop (or always, when [force]). True if one was applied. */
    private suspend fun pull(force: Boolean, onlyIfExists: Boolean = false): Boolean = mutex.withLock {
        val token = token() ?: return@withLock false
        _state.value = _state.value.copy(busy = true, message = if (force && !onlyIfExists) "Restoring…" else null)
        val result = runCatching {
            val remote = remoteCopy(token) ?: return@runCatching null
            val fromHere = remote.str("deviceId") == deviceId
            val remoteData = remote["data"] as? JsonObject ?: return@runCatching false
            val base = readBase()
            if (!force && (fromHere || remoteData.toString() == base?.toString())) return@runCatching false
            if (!LiveTvSyncLocal.syncHasSources(remoteData)) return@runCatching null
            // Restore replaces this desktop's setup; automatic sync keeps changes made here.
            val data = if (force) remoteData else LiveTvMerge.merge(
                base, LiveTvSyncLocal.exportForSync(), remoteData,
                localWinsWithoutBase = false, removedHere = LiveTvSyncLocal.removedSourceIds(),
            )
            applyLocally(data)
            store.putString(KEY_LAST_SYNC, System.currentTimeMillis().toString())
            store.putString(KEY_LAST_HASH, sha(LiveTvSyncLocal.exportForSync().toString()))
            writeBase(remoteData)
            if (data.toString() != remoteData.toString()) scope.launch { delay(2_000); push(force = false) }
            true
        }
        val applied = result.getOrNull() == true
        _state.value = _state.value.copy(
            busy = false,
            lastSyncMs = store.getString(KEY_LAST_SYNC)?.toLongOrNull() ?: 0L,
            message = when {
                result.isFailure -> "Restore failed: ${result.exceptionOrNull()?.message ?: "network error"}"
                result.getOrNull() == null && force && !onlyIfExists -> "No backup found in Google Drive yet."
                applied && force -> "Restored from Google Drive."
                applied -> "Updated from your other device."
                else -> _state.value.message
            },
        )
        applied
    }

    internal fun applyLocally(data: JsonObject) {
        applying = true
        try {
            LiveTvSyncLocal.importFromSync(data)
        } finally {
            applying = false
        }
    }

    /** The Live TV setup saved in Google Drive, or null. Changes nothing. */
    suspend fun backupData(): JsonObject? = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext null
        remoteCopy(token)?.get("data") as? JsonObject
    }

    private suspend fun remoteCopy(token: String): JsonObject? = runCatching {
        val fileId = fileId(token) ?: return null
        LiveTvMerge.parse(
            execute(
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build(),
            ),
        )
    }.getOrNull()

    private fun baseFile() = IptvSourceStore.dataDir().resolve("livetv-drive-base.json")

    private fun readBase(): JsonObject? = runCatching {
        if (!Files.isRegularFile(baseFile())) null else LiveTvMerge.parse(Files.readString(baseFile()))
    }.getOrNull()

    private fun writeBase(data: JsonObject) {
        runCatching {
            Files.createDirectories(baseFile().parent)
            Files.writeString(baseFile(), data.toString())
            IptvSourceStore.setOwnerOnly(baseFile())
        }
    }

    // ------------------------------------------------------------------ Google plumbing

    private suspend fun token(): String? {
        val refresh = refreshToken() ?: return null
        accessToken?.let { if (System.currentTimeMillis() < accessTokenExpiresAt - 60_000) return it }
        val res = runCatching {
            postForm(
                "https://oauth2.googleapis.com/token",
                mapOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "refresh_token" to refresh,
                    "grant_type" to "refresh_token",
                ),
            )
        }.getOrElse { e ->
            val why = when (e) {
                is java.net.SocketTimeoutException -> "timed out"
                is java.net.UnknownHostException -> "couldn't look up Google's address"
                is javax.net.ssl.SSLException -> "secure connection refused"
                is java.net.ConnectException -> "connection refused"
                else -> e.javaClass.simpleName
            }
            _state.value = _state.value.copy(message = "Couldn't reach Google Drive ($why).")
            return null
        }
        if (res.str("error") == "invalid_grant") {
            store.remove(KEY_REFRESH)
            _state.value = _state.value.copy(connected = false, message = "Google Drive access was removed. Connect again to keep syncing.")
            return null
        }
        val access = res.str("access_token")?.takeIf { it.isNotBlank() } ?: return null
        accessToken = access
        accessTokenExpiresAt = System.currentTimeMillis() + (res.long("expires_in") ?: 3600L) * 1000
        return access
    }

    private suspend fun fileId(token: String): String? {
        store.getString(KEY_FILE_ID)?.let { return it }
        val text = execute(
            Request.Builder()
                .url(
                    "https://www.googleapis.com/drive/v3/files?spaces=${if (isAppDataMode) "appDataFolder" else "drive"}" +
                        "&q=" + URLEncoder.encode("name='$FILE_NAME' and trashed=false", "UTF-8").replace("+", "%20") +
                        "&fields=files(id)",
                )
                .header("Authorization", "Bearer $token")
                .get()
                .build(),
        )
        val files = LiveTvMerge.parse(text)?.get("files") as? JsonArray
        val id = (files?.firstOrNull() as? JsonObject)?.str("id")?.takeIf { it.isNotBlank() } ?: return null
        store.putString(KEY_FILE_ID, id)
        return id
    }

    private suspend fun postForm(url: String, fields: Map<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        http.newCall(Request.Builder().url(url).post(form).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            LiveTvMerge.parse(text) ?: buildJsonObject { put("error", "http_${r.code}") }
        }
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 404) store.remove(KEY_FILE_ID)
            if (!r.isSuccessful) error("HTTP ${r.code}")
            text
        }
    }

    private val isAppDataMode: Boolean
        get() = (store.getString(KEY_MODE) ?: MODE_APPDATA) == MODE_APPDATA

    private fun googleError(json: JsonObject): String {
        val e = json.str("error").orEmpty().ifBlank { json.str("error_code").orEmpty() }
        val detail = json.str("error_description")?.takeIf { it.isNotBlank() }?.let { " Google says: $it" }.orEmpty()
        return when (e) {
            "invalid_client" -> "Google doesn't recognize this build's sign-in client.$detail"
            "unauthorized_client" -> "This sign-in client isn't allowed to use device sign-in.$detail"
            "invalid_scope" -> "Google refused the Drive permission for this app.$detail"
            "rate_limit_exceeded" -> "Too many attempts. Wait a minute and try again."
            else -> "Google sign-in failed ($e).$detail"
        }
    }

    private fun emailFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split('.')[1]
        val text = String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))
        LiveTvMerge.parse(text)?.str("email")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun sha(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    const val DEVICE_NAME = "Nuvio One for Windows"
    private const val MODE_APPDATA = "appdata"
    private const val MODE_FILE = "file"
    /** As the TVs do now: Google's device sign-in refuses drive.appdata, so a normal Drive file comes first. */
    private val SCOPE_ATTEMPTS = listOf(
        "openid email https://www.googleapis.com/auth/drive.file" to MODE_FILE,
        "openid email https://www.googleapis.com/auth/drive.appdata" to MODE_APPDATA,
    )
    private const val FILE_NAME = "Nuvio Live TV backup.json"
    private val JSON = "application/json; charset=UTF-8".toMediaType()
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EMAIL = "email"
    private const val KEY_AUTO = "auto_sync"
    private const val KEY_FILE_ID = "file_id"
    private const val KEY_LAST_HASH = "last_hash"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_MODE = "mode"
    private const val KEY_CLIENT_ID = "client_id"
}
