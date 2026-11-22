package com.nuvio.app.core.diagnostics

import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.iptv.IptvRecorder
import com.nuvio.app.features.iptv.IptvSession
import com.nuvio.app.features.iptv.IptvSmbStore
import com.nuvio.app.features.iptv.IptvSourceStore
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Builds a redacted desktop debug report and sends it to support.
 *
 * The preferred destination is the same Discord webhook the TV and mobile apps use. A build
 * without one falls back to a Sentry event, and a build with neither saves the report under
 * the app's data folder so it can still be attached to a support request.
 */
internal object DesktopDebugReport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun send(): Result<DebugReportReceipt> = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)
            val fileName = "desktop-debug-report-$id.txt"
            val text = DebugReportRedactor.redact(build(id))
            val webhook = DebugReportConfig.DISCORD_WEBHOOK.trim()
            if (webhook.isNotBlank()) {
                upload(webhook, id, fileName, text)
                return@runCatching DebugReportReceipt(id = id, sent = true)
            }
            if (Sentry.isEnabled()) {
                Sentry.captureMessage(text.take(50_000), SentryLevel.INFO)
                Sentry.flush(5_000)
                return@runCatching DebugReportReceipt(id = id, sent = true)
            }
            // Last resort: keep the report on the computer so support can ask for it.
            val dir = DesktopStorage.rootDir.resolve("debug-reports").also { Files.createDirectories(it) }
            val file = dir.resolve(fileName)
            Files.writeString(file, text)
            DebugReportReceipt(id = id, sent = false, detail = file.toString())
        }
    }

    private fun build(id: String): String = buildString {
        appendLine("Nuvio One Desktop debug report")
        appendLine("ID: $id")
        appendLine("Generated: ${timestamp()}")

        section("APP")
        appendLine("desktopVersion=${AppVersionConfig.DESKTOP_VERSION_NAME} (${AppVersionConfig.DESKTOP_VERSION_CODE})")
        appendLine("baseVersion=${AppVersionConfig.VERSION_NAME} (${AppVersionConfig.VERSION_CODE})")
        appendLine("package=com.nuvio.media.desktop")
        appendLine("environment=${SentryConfig.ENVIRONMENT}")
        appendLine("sentry=${if (Sentry.isEnabled()) "enabled" else "disabled"}")
        appendLine(
            "reportUpload=" + when {
                DebugReportConfig.DISCORD_WEBHOOK.isNotBlank() -> "webhook"
                Sentry.isEnabled() -> "sentry"
                else -> "local file"
            }
        )
        when (val auth = AuthRepository.state.value) {
            is AuthState.Authenticated -> {
                appendLine("account=" + if (auth.isAnonymous) "local profile" else "signed in")
                if (!auth.isAnonymous) {
                    // Stable, non-reversible: support can find the sync row without the UUID.
                    appendLine("accountSupportCode=${SupportCode.forUserId(auth.userId)}")
                }
            }
            AuthState.Unauthenticated -> appendLine("account=signed out")
            AuthState.Loading -> appendLine("account=loading")
        }

        section("DEVICE")
        appendLine("os=${System.getProperty("os.name").orEmpty()} ${System.getProperty("os.version").orEmpty()}")
        appendLine("architecture=${System.getProperty("os.arch").orEmpty()}")
        appendLine("java=${System.getProperty("java.version").orEmpty()}")
        appendLine("locale=${Locale.getDefault().toLanguageTag()}")
        appendLine("timezone=${TimeZone.getDefault().id}")
        appendLine("processors=${Runtime.getRuntime().availableProcessors()}")
        appendLine("heapMaxMb=${Runtime.getRuntime().maxMemory() / 1024 / 1024}")
        appendLine("heapFreeMb=${Runtime.getRuntime().freeMemory() / 1024 / 1024}")

        section("LIVE TV")
        appendLine("sources=${runCatching { IptvSourceStore.load().size }.getOrDefault(0)}")
        appendLine("sourceStore=${runCatching { IptvSourceStore.diagnostics() }.getOrElse { "unavailable" }}")
        appendLine("channels=${runCatching { IptvSession.guide.channels.size }.getOrDefault(0)}")
        appendLine("programs=${runCatching { IptvSession.guide.programs.values.sumOf { it.size } }.getOrDefault(0)}")
        appendLine("lastLoadError=${runCatching { IptvSession.guide.errors.firstOrNull() }.getOrNull() ?: "none"}")
        appendLine("localRecordings=${runCatching { IptvRecorder.recordings().size }.getOrDefault(0)}")
        appendLine("recordingActive=${runCatching { IptvRecorder.recording }.getOrDefault(false)}")
        appendLine("recordingNotice=${runCatching { IptvRecorder.notice }.getOrNull()?.take(300) ?: "none"}")
        appendLine("recordingError=${runCatching { IptvRecorder.error }.getOrNull()?.take(300) ?: "none"}")
        appendLine("smbConfigured=${runCatching { IptvSmbStore.load() != null }.getOrDefault(false)}")

        section("RECENT LOG")
        appendLine(recentLog())

        section("NOTES")
        appendLine("Reports are redacted before upload. Add what you were doing when the problem happened.")
    }

    private fun upload(webhook: String, id: String, fileName: String, text: String) {
        val url = webhook.toHttpUrlOrNull()
            ?: error("The debug report destination is not a valid URL")
        val version = "${AppVersionConfig.DESKTOP_VERSION_NAME} (${AppVersionConfig.DESKTOP_VERSION_CODE})"
        val payload = """{"content":"Debug report `$id` - $version","username":"Nuvio One Desktop Debug","allowed_mentions":{"parse":[]}}"""
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("payload_json", payload)
            .addFormDataPart(
                "file",
                fileName,
                text.toRequestBody("text/plain; charset=utf-8".toMediaType()),
            )
            .build()
        client.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
            if (!response.isSuccessful) error("Upload failed (HTTP ${response.code})")
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())

    private fun recentLog(): String = runCatching {
        val file = File(DesktopStorage.rootDir.toFile(), "onetube-debug.log")
        if (!file.isFile) return@runCatching "No recent log file."
        val length = file.length()
        val start = (length - 60_000L).coerceAtLeast(0L)
        file.inputStream().use { input ->
            input.skip(start)
            input.bufferedReader().readText().takeLast(60_000)
        }
    }.getOrElse { "Log unavailable (${it.javaClass.simpleName})." }

    private fun StringBuilder.section(name: String) {
        appendLine()
        appendLine("== $name ==")
    }
}

/** Removes credentials and personal data before a report leaves the computer. */
internal object DebugReportRedactor {
    private val webhook = DebugReportConfig.DISCORD_WEBHOOK.trim()

    private val bearer = Regex("""(?i)\b(bearer\s+)[A-Za-z0-9._~+/=-]{8,}""")
    private val keyValue = Regex(
        """(?i)\b(authorization|proxy-authorization|x-api-key|set-cookie|cookie|access[_-]?token|refresh[_-]?token|api[_-]?key|apikey|debrid[_-]?key|premiumize[_-]?key|torbox[_-]?key|rd[_-]?key|password|passwd|secret|token|auth)(\s*[=:]\s*)("[^"]*"|'[^']*'|[^\s&,;]+)"""
    )
    private val querySecret = Regex(
        """(?i)([?&](?:access[_-]?token|refresh[_-]?token|api[_-]?key|apikey|token|secret|password|auth|code)=)[^&\s]+"""
    )
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val url = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val ipv4 = Regex(
        """(?<![\d.])(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)(?![\d.])"""
    )
    private val uuid = Regex("""(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""")
    private val longToken = Regex("""\b[A-Za-z0-9_-]{48,}\b""")
    private val windowsUser = Regex("""(?i)([A-Z]:\\Users\\)[^\\\s]+""")

    fun redact(text: String): String {
        var out = text
        if (webhook.isNotBlank()) out = out.replace(webhook, "[redacted webhook]")
        out = bearer.replace(out) { "${it.groupValues[1]}[redacted]" }
        out = keyValue.replace(out) { "${it.groupValues[1]}${it.groupValues[2]}[redacted]" }
        out = querySecret.replace(out) { "${it.groupValues[1]}[redacted]" }
        out = url.replace(out) { match ->
            val value = match.value
            val scheme = value.indexOf("://")
            if (scheme < 0) return@replace value
            val hostStart = scheme + 3
            val hostEnd = value.indexOfAny(charArrayOf('/', '?', '#'), hostStart)
                .let { if (it < 0) value.length else it }
            val host = value.substring(hostStart, hostEnd).substringAfterLast('@')
            value.substring(0, hostStart) + host + "/[redacted]"
        }
        out = email.replace(out, "[email]")
        out = ipv4.replace(out, "[ip]")
        out = uuid.replace(out, "[id]")
        out = longToken.replace(out, "[redacted]")
        out = windowsUser.replace(out) { "${it.groupValues[1]}[user]" }
        return out
    }
}
