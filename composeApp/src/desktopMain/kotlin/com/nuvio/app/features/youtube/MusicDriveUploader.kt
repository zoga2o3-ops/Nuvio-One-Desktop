package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopSecretStore
import com.nuvio.app.core.storage.DesktopStorage
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class MusicDriveSignIn(
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
)

internal data class MusicDriveState(
    val configured: Boolean = MusicDriveConfig.CLIENT_ID.isNotBlank() && MusicDriveConfig.CLIENT_SECRET.isNotBlank(),
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val signIn: MusicDriveSignIn? = null,
    val folderUrl: String? = null,
    val progress: Float? = null,
    val progressLabel: String? = null,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Copies the local music library into a "Nuvio Music" folder in the user's Google Drive.
 *
 * Sign-in uses the OAuth "TVs and limited input devices" device flow, the same flow the TV app
 * uses for its Live TV backup: the desktop shows a short code, the user approves it on any device,
 * and only a refresh token is kept (encrypted with DPAPI on Windows). The `drive.file` scope means
 * Nuvio can only see files and folders it created itself.
 */
internal object MusicDriveUploader {
    private const val DeviceCodeUrl = "https://oauth2.googleapis.com/device/code"
    private const val TokenUrl = "https://oauth2.googleapis.com/token"
    private const val FilesUrl = "https://www.googleapis.com/drive/v3/files"
    private const val UploadUrl = "https://www.googleapis.com/upload/drive/v3/files"
    private const val Scope = "openid email https://www.googleapis.com/auth/drive.file"
    private const val FolderName = "Nuvio Music"
    private const val KeyRefresh = "refresh_token"
    private const val KeyClient = "client_id"
    private const val KeyFolder = "folder_id"

    private val store = DesktopStorage.store("nuvio_music_drive")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var state by mutableStateOf(MusicDriveState())
        private set

    private var accessToken: String? = null
    private var accessTokenExpiresAt = 0L
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val clientId = MusicDriveConfig.CLIENT_ID
            if (store.getString(KeyClient) != clientId) {
                store.putString(KeyClient, clientId)
                store.putString(KeyRefresh, "")
                store.putString(KeyFolder, "")
            }
            val refresh = DesktopSecretStore.unprotect(store.getString(KeyRefresh).orEmpty())
            val folderId = store.getString(KeyFolder).orEmpty().takeIf { it.isNotBlank() }
            state = state.copy(
                signedIn = refresh.isNotBlank(),
                folderUrl = folderId?.let { "https://drive.google.com/drive/folders/$it" },
            )
        }
    }

    fun signIn() {
        ensureLoaded()
        if (!state.configured) {
            state = state.copy(error = "This build has no Google Drive credentials.")
            return
        }
        if (state.busy || state.signIn != null) return
        scope.launch {
            state = state.copy(busy = true, error = null, message = null)
            runCatching {
                val response = postForm(
                    DeviceCodeUrl,
                    mapOf(
                        "client_id" to MusicDriveConfig.CLIENT_ID,
                        "scope" to Scope,
                    ),
                )
                val deviceCode = response.string("device_code")
                    ?: error(response.string("error_description") ?: "Google did not return a device code.")
                val userCode = response.string("user_code") ?: error("Google did not return a user code.")
                val verification = response.string("verification_url")
                    ?: response.string("verification_uri")
                    ?: "https://www.google.com/device"
                val interval = response.int("interval") ?: 5
                val expires = response.int("expires_in") ?: 1800
                state = state.copy(
                    busy = false,
                    signIn = MusicDriveSignIn(userCode, verification, expires),
                )
                pollForToken(deviceCode, interval.toLong(), expires.toLong())
            }.onFailure {
                state = state.copy(
                    busy = false,
                    signIn = null,
                    error = it.message ?: "Google Drive sign-in failed.",
                )
            }
        }
    }

    private suspend fun pollForToken(deviceCode: String, intervalSeconds: Long, expiresSeconds: Long) {
        val deadline = System.currentTimeMillis() + expiresSeconds * 1000L
        var waitMs = (intervalSeconds * 1000L).coerceAtLeast(2_000L)
        while (System.currentTimeMillis() < deadline) {
            delay(waitMs)
            val response = runCatching {
                postForm(
                    TokenUrl,
                    mapOf(
                        "client_id" to MusicDriveConfig.CLIENT_ID,
                        "client_secret" to MusicDriveConfig.CLIENT_SECRET,
                        "device_code" to deviceCode,
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    ),
                )
            }.getOrElse {
                state = state.copy(signIn = null, error = it.message ?: "Could not reach Google.")
                return
            }
            when (val error = response.string("error")) {
                null -> {
                    onSignedIn(response)
                    return
                }
                "authorization_pending" -> Unit
                "slow_down" -> waitMs += 5_000L
                "expired_token" -> {
                    state = state.copy(signIn = null, error = "The Google sign-in code expired. Try again.")
                    return
                }
                "access_denied" -> {
                    state = state.copy(signIn = null, error = "Google Drive access was declined.")
                    return
                }
                else -> {
                    state = state.copy(
                        signIn = null,
                        error = response.string("error_description") ?: "Google returned $error.",
                    )
                    return
                }
            }
        }
        state = state.copy(signIn = null, error = "The Google sign-in code expired. Try again.")
    }

    private fun onSignedIn(response: JsonObject) {
        val refresh = response.string("refresh_token")
        val token = response.string("access_token")
        if (refresh.isNullOrBlank() || token.isNullOrBlank()) {
            state = state.copy(signIn = null, error = "Google did not return a usable session.")
            return
        }
        store.putString(KeyRefresh, DesktopSecretStore.protect(refresh))
        store.putString(KeyClient, MusicDriveConfig.CLIENT_ID)
        store.putString(KeyFolder, "")
        accessToken = token
        accessTokenExpiresAt = System.currentTimeMillis() + ((response.int("expires_in") ?: 3600) * 1000L) - 60_000L
        state = state.copy(
            signedIn = true,
            busy = false,
            signIn = null,
            message = "Connected to Google Drive.",
            error = null,
            folderUrl = null,
        )
    }

    fun signOut() {
        ensureLoaded()
        store.putString(KeyRefresh, "")
        store.putString(KeyFolder, "")
        accessToken = null
        accessTokenExpiresAt = 0L
        state = MusicDriveState(
            configured = state.configured,
            message = "Signed out of Google Drive.",
        )
    }

    fun uploadLocalLibrary() {
        ensureLoaded()
        if (!state.signedIn) {
            state = state.copy(error = "Connect Google Drive first.")
            return
        }
        if (state.busy) return
        scope.launch {
            state = state.copy(busy = true, error = null, message = null, progress = 0f, progressLabel = "Preparing…")
            runCatching {
                val token = accessToken()
                val folderId = ensureFolder(token)
                MusicLocalRepository.ensureLoaded()
                val tracks = MusicLocalRepository.tracks
                if (tracks.isEmpty()) error("No local tracks to upload. Scan a folder in Local first.")
                var uploaded = 0
                var skipped = 0
                var failed = 0
                tracks.forEachIndexed { index, track ->
                    state = state.copy(
                        progress = index.toFloat() / tracks.size,
                        progressLabel = "Uploading ${index + 1} of ${tracks.size}",
                    )
                    val file = runCatching { Path.of(track.path) }.getOrNull()
                    if (file == null || !Files.isRegularFile(file)) {
                        skipped++
                        return@forEachIndexed
                    }
                    runCatching { uploadTrack(token, folderId, track, file) }
                        .onSuccess { uploaded++ }
                        .onFailure { failed++; if (failed == 1) state = state.copy(error = it.message) }
                }
                state = state.copy(
                    busy = false,
                    progress = 1f,
                    progressLabel = null,
                    message = when {
                        failed > 0 -> "Uploaded $uploaded tracks; $failed failed; $skipped missing."
                        uploaded == 0 -> "Nothing new to upload."
                        else -> "Uploaded $uploaded tracks to Google Drive."
                    },
                )
            }.onFailure {
                state = state.copy(
                    busy = false,
                    progress = null,
                    progressLabel = null,
                    error = it.message ?: "Could not upload to Google Drive.",
                )
            }
        }
    }

    private fun accessToken(): String {
        val current = accessToken
        if (!current.isNullOrBlank() && System.currentTimeMillis() < accessTokenExpiresAt) return current
        val refresh = DesktopSecretStore.unprotect(store.getString(KeyRefresh).orEmpty())
        if (refresh.isBlank()) error("Connect Google Drive first.")
        val response = postForm(
            TokenUrl,
            mapOf(
                "client_id" to MusicDriveConfig.CLIENT_ID,
                "client_secret" to MusicDriveConfig.CLIENT_SECRET,
                "refresh_token" to refresh,
                "grant_type" to "refresh_token",
            ),
        )
        val token = response.string("access_token") ?: error("Google Drive session expired. Sign in again.")
        accessToken = token
        accessTokenExpiresAt = System.currentTimeMillis() + ((response.int("expires_in") ?: 3600) * 1000L) - 60_000L
        return token
    }

    private fun ensureFolder(token: String): String {
        store.getString(KeyFolder)?.takeIf { it.isNotBlank() }?.let { return it }
        val query = URLEncoder.encode(
            "name='${FolderName.replace("'", "\\'")}' and mimeType='application/vnd.google-apps.folder' and trashed=false",
            Charsets.UTF_8.name(),
        )
        val existing = getJson(
            "$FilesUrl?q=$query&spaces=drive&fields=files(id,name,webViewLink)",
            token,
        )
        val first = (existing["files"] as? JsonArray)?.firstOrNull()?.jsonObject
        val existingId = first?.string("id")
        if (!existingId.isNullOrBlank()) {
            store.putString(KeyFolder, existingId)
            state = state.copy(folderUrl = first?.string("webViewLink"))
            return existingId
        }
        val created = postJson(
            "$FilesUrl?fields=id,webViewLink",
            token,
            buildJsonObject {
                put("name", FolderName)
                put("mimeType", "application/vnd.google-apps.folder")
            }.toString(),
        )
        val id = created.string("id") ?: error("Google did not create the Nuvio Music folder.")
        store.putString(KeyFolder, id)
        state = state.copy(folderUrl = created.string("webViewLink"))
        return id
    }

    private fun uploadTrack(token: String, folderId: String, track: LocalMusicTrack, file: Path): String {
        val extension = file.fileName.toString().substringAfterLast('.', "")
        val name = listOf(track.artist, track.title)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .ifBlank { file.fileName.toString().substringBeforeLast('.') } +
            if (extension.isBlank()) "" else ".$extension"
        val metadata = buildJsonObject {
            put("name", name)
            put("parents", buildJsonArray { add(JsonPrimitive(folderId)) })
        }.toString()
        val connection = openConnection("$UploadUrl?uploadType=resumable&fields=id,name,webViewLink")
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("X-Upload-Content-Type", contentType(file))
            connection.setRequestProperty("X-Upload-Content-Length", Files.size(file).toString())
            connection.outputStream.use { it.write(metadata.toByteArray(Charsets.UTF_8)) }
            require(connection.responseCode in 200..299) {
                "Drive init returned HTTP ${connection.responseCode}."
            }
            val location = connection.getHeaderField("Location")
                ?: error("Drive did not return an upload URL.")
            return putFile(location, token, file)
        } finally {
            connection.disconnect()
        }
    }

    private fun putFile(location: String, token: String, file: Path): String {
        val connection = openConnection(location)
        try {
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", contentType(file))
            connection.setRequestProperty("Content-Length", Files.size(file).toString())
            connection.outputStream.use { output ->
                Files.newInputStream(file).use { input -> input.copyTo(output) }
            }
            val body = connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            require(connection.responseCode in 200..299) {
                "Drive upload returned HTTP ${connection.responseCode}."
            }
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            return root?.string("id") ?: ""
        } finally {
            connection.disconnect()
        }
    }

    private fun getJson(url: String, token: String): JsonObject {
        val connection = openConnection(url)
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            require(connection.responseCode in 200..299) {
                "Drive returned HTTP ${connection.responseCode}."
            }
            json.parseToJsonElement(connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)).jsonObject
        } finally {
            connection.disconnect()
        }
    }

    private fun postJson(url: String, token: String, body: String): JsonObject {
        val connection = openConnection(url)
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            require(connection.responseCode in 200..299) {
                "Drive returned HTTP ${connection.responseCode}."
            }
            json.parseToJsonElement(connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)).jsonObject
        } finally {
            connection.disconnect()
        }
    }

    private fun postForm(url: String, params: Map<String, String>): JsonObject {
        val form = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8.name())}=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        val connection = openConnection(url)
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty()
            val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { JsonObject(emptyMap()) }
            if (connection.responseCode !in 200..299 && parsed["error"] == null) {
                error("Google returned HTTP ${connection.responseCode}.")
            }
            parsed
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "NuvioOneDesktop/0.1")
        return connection
    }

    private fun contentType(file: Path): String = when (file.fileName.toString().substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "m4b", "aac" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "webm" -> "audio/webm"
        else -> "application/octet-stream"
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
}
