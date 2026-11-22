package com.nuvio.app.features.iptv

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.jsonPrimitive

internal object IptvLoader {
    private const val MAX_REDIRECTS = 8
    private const val MAX_VOD_ITEMS = 20_000
    private const val PLAYLIST_WEIGHT = 0.75f
    private const val EMIT_INTERVAL_MS = 120L
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 180_000

    private val VOD_PATH = Regex("""/(movie|movies|series|vod)/""", RegexOption.IGNORE_CASE)
    private val VOD_EXT = Regex("""\.(mp4|mkv|avi|mov|wmv|m4v|webm|flv)(\?|$)""", RegexOption.IGNORE_CASE)

    /** Movies and episodes in an m3u_plus link aren't TV channels. */
    fun isVodEntry(url: String): Boolean = VOD_PATH.containsMatchIn(url) || VOD_EXT.containsMatchIn(url)

    private const val SOURCE_PARALLELISM = 3
    private const val GUIDE_PARALLELISM = 3

    /** What one source contributed once its full playlist has been read. */
    private class SourceResult(
        val channels: List<LiveChannel>,
        val movies: List<VodItem>,
        val epgUrls: List<String>,
        val errors: List<String>,
        val programs: Map<String, List<EpgProgram>> = emptyMap(),
    )

    suspend fun load(
        sources: List<IptvSource>,
        onProgress: (IptvLoadProgress) -> Unit = {},
        onPlaylistsReady: (IptvGuide) -> Unit = {},
        guideFromMs: Long = System.currentTimeMillis() - 7L * 86_400_000,
        guideUntilMs: Long = System.currentTimeMillis() + 7L * 86_400_000,
    ): IptvGuide = withContext(Dispatchers.IO) {
        // The same playlist added on two devices arrives twice through sync (different ids).
        // Loading it twice doubles every channel, and providers answer the extra downloads with
        // HTTP 429, so each address + login loads once (the first entry, with any guide link).
        val orderedSources = distinctSources(sources).sortedBy { if (it.kind.equals("xtream", true)) 0 else 1 }
        val sourceCount = orderedSources.size
        val lock = Any()
        // Per-source state, guarded by [lock].
        val results = arrayOfNulls<SourceResult>(sourceCount)
        val previews = arrayOfNulls<List<LiveChannel>>(sourceCount)
        val fractions = FloatArray(sourceCount)
        val vodBudget = java.util.concurrent.atomic.AtomicInteger(MAX_VOD_ITEMS)

        fun countsLocked(): Pair<Int, Int> {
            var ch = 0
            var mv = 0
            for (i in 0 until sourceCount) {
                val r = results[i]
                if (r != null) {
                    ch += r.channels.size
                    mv += r.movies.size
                } else {
                    ch += previews[i]?.size ?: 0
                }
            }
            return ch to mv
        }

        fun reportPlaylists(detail: String) {
            val (fraction, counts) = synchronized(lock) {
                (fractions.sum() / sourceCount.coerceAtLeast(1)) to countsLocked()
            }
            onProgress(
                IptvLoadProgress(
                    title = "Reading playlists",
                    detail = detail,
                    sourceIndex = results.count { it != null },
                    sourceCount = sourceCount,
                    channels = counts.first,
                    movies = counts.second,
                    fraction = PLAYLIST_WEIGHT * fraction,
                ),
            )
        }

        // Publish everything usable so far: finished sources plus the quick
        // Xtream previews of sources still downloading their full playlist.
        fun publish() {
            val guide = synchronized(lock) {
                val ch = ArrayList<LiveChannel>()
                val mv = ArrayList<VodItem>()
                val errs = ArrayList<String>()
                for (i in 0 until sourceCount) {
                    val r = results[i]
                    if (r != null) {
                        ch += r.channels
                        mv += r.movies
                        errs += r.errors
                    } else {
                        previews[i]?.let { ch += it }
                    }
                }
                IptvGuide(
                    channels = ch.sortedWith(compareBy(LiveChannel::sourceName, LiveChannel::group, LiveChannel::number)),
                    programs = emptyMap(),
                    errors = errs,
                    movies = mv.sortedWith(compareBy(VodItem::sourceName, VodItem::group, VodItem::name)),
                )
            }
            onPlaylistsReady(guide)
        }

        reportPlaylists(if (orderedSources.isEmpty()) "No sources" else "${orderedSources.first().name} · 1 of $sourceCount")

        coroutineScope {
            // Guides: start every URL known up front right away (an Xtream
            // server's xmltv.php, a source's own EPG link) instead of after all
            // playlists; URLs found inside playlists join as they appear.
            val guideGate = Semaphore(GUIDE_PARALLELISM)
            val guideJobs = LinkedHashMap<String, Deferred<Result<XmltvGuide>>>()
            fun startGuide(url: String) = synchronized(guideJobs) {
                guideJobs.getOrPut(url) {
                    async(Dispatchers.IO) {
                        guideGate.withPermit {
                            runCatching {
                                var parsed: XmltvGuide? = null
                                var lastError: IOException? = null
                                for (attempt in 0..1) {
                                    try {
                                        open(url).use { input ->
                                            parsed = XmltvGuideParser.parse(input, guideFromMs, guideUntilMs)
                                        }
                                        break
                                    } catch (e: IOException) {
                                        lastError = e
                                        if (attempt == 0) delay(900)
                                    }
                                }
                                parsed ?: throw (lastError ?: IOException("Guide download failed"))
                            }
                        }
                    }
                }
            }
            orderedSources.forEach { source ->
                if (source.epgUrl.isNotBlank()) startGuide(source.epgUrl)
                if (source.kind == "xtream") runCatching { startGuide(xtreamUrl(source, "xmltv.php")) }
            }
            // Guides of their own (from a TV, through Live TV sync).
            if (sources.isNotEmpty()) {
                IptvGuideSourceStore.load().filter { it.enabled && it.url.isNotBlank() }.forEach { startGuide(it.url) }
            }

            // Xtream preview: the JSON live-stream list is a fraction of the
            // m3u_plus download (which also carries every movie and episode),
            // so show those channels while the full playlist is still coming.
            orderedSources.forEachIndexed { index, source ->
                if (source.kind != "xtream") return@forEachIndexed
                launch(Dispatchers.IO) {
                    val preview = runCatching { xtreamLivePreview(source) }.getOrNull()
                    if (preview.isNullOrEmpty()) return@launch
                    val show = synchronized(lock) {
                        if (results[index] == null) {
                            previews[index] = preview
                            true
                        } else {
                            false
                        }
                    }
                    if (show) {
                        publish()
                        reportPlaylists("${source.name} · channels ready, loading the full playlist")
                    }
                }
            }

            // Full playlists, a few sources at a time.
            val sourceGate = Semaphore(SOURCE_PARALLELISM)
            orderedSources.mapIndexed { index, source ->
                async(Dispatchers.IO) {
                    sourceGate.withPermit {
                        val result = loadSource(source, index, vodBudget) { within ->
                            synchronized(lock) { fractions[index] = within }
                            reportPlaylists("${source.name} · ${index + 1} of $sourceCount")
                        }
                        synchronized(lock) {
                            results[index] = result
                            previews[index] = null
                            fractions[index] = 1f
                        }
                        result.epgUrls.forEach { startGuide(it) }
                        reportPlaylists("${source.name} · ${index + 1} of $sourceCount")
                        publish()
                    }
                }
            }.awaitAll()

            // Assemble the final guide.
            val channels = ArrayList<LiveChannel>()
            val movies = ArrayList<VodItem>()
            val errors = ArrayList<String>()
            results.forEach { r ->
                if (r != null) {
                    channels += r.channels
                    movies += r.movies
                    errors += r.errors
                }
            }
            val jobs = synchronized(guideJobs) { guideJobs.entries.toList() }
            val epgById = LinkedHashMap<String, List<EpgProgram>>()
            val idsByName = LinkedHashMap<String, String>()
            results.forEach { r ->
                r?.programs?.forEach { (id, programs) -> epgById[id] = programs }
            }
            jobs.forEachIndexed { index, (_, job) ->
                onProgress(
                    IptvLoadProgress(
                        title = "Loading guide",
                        detail = "Guide ${index + 1} of ${jobs.size}",
                        sourceIndex = index,
                        sourceCount = jobs.size,
                        channels = channels.size,
                        movies = movies.size,
                        fraction = PLAYLIST_WEIGHT +
                            (1f - PLAYLIST_WEIGHT) * (index.toFloat() / jobs.size.coerceAtLeast(1)),
                    ),
                )
                job.await()
                    .onSuccess { guide ->
                        guide.programs.forEach { (id, programs) -> epgById[id] = programs }
                        idsByName.putAll(guide.idsByName)
                    }
                    .onFailure { e -> errors += "Guide ${index + 1}: ${safeError(e as? Exception ?: Exception(e))}" }
            }
            val programs = channels.associate { channel ->
                val id = channel.tvgId?.takeIf { it in epgById }
                    ?: channel.tvgName?.lowercase(Locale.ROOT)?.let(idsByName::get)
                    ?: idsByName[channel.name.lowercase(Locale.ROOT)]
                channel.key to (id?.let(epgById::get) ?: emptyList())
            }
            onProgress(
                IptvLoadProgress(
                    title = "Ready",
                    detail = "${channels.size} channels · ${movies.size} titles",
                    sourceIndex = sourceCount,
                    sourceCount = sourceCount,
                    channels = channels.size,
                    movies = movies.size,
                    fraction = 1f,
                ),
            )
            IptvGuide(
                channels.sortedWith(compareBy(LiveChannel::sourceName, LiveChannel::group, LiveChannel::number)),
                programs,
                errors,
                movies.sortedWith(compareBy(VodItem::sourceName, VodItem::group, VodItem::name)),
            )
        }
    }

    /** Reads one source's full playlist (with one retry). Never throws. */
    private suspend fun loadSource(
        source: IptvSource,
        sourceIndex: Int,
        vodBudget: java.util.concurrent.atomic.AtomicInteger,
        onFraction: (Float) -> Unit,
    ): SourceResult {
        if (source.kind.equals("stalker", true)) {
            return loadStalkerSource(source, onFraction)
        }
        val playlistUrl = try {
            if (source.kind == "xtream") {
                xtreamUrl(source, "get.php", "type=m3u_plus&output=ts")
            } else {
                normalizePlaylistUrl(source.url)
            }
        } catch (e: Exception) {
            return SourceResult(emptyList(), emptyList(), emptyList(), listOf("${source.name}: ${safeError(e)}"))
        }
        var lastError: Exception? = null
        for (attempt in 0..1) {
            try {
                var totalBytes = 0L
                var lastEmit = 0L
                val sourceMovies = ArrayList<VodItem>()
                val playlist = open(playlistUrl, onLength = { totalBytes = it }).use { raw ->
                    val input = CountingInputStream(raw) { read ->
                        val nowMs = System.currentTimeMillis()
                        val finished = totalBytes > 0 && read >= totalBytes
                        if (!finished && nowMs - lastEmit < EMIT_INTERVAL_MS) return@CountingInputStream
                        lastEmit = nowMs
                        onFraction(if (totalBytes > 0) (read.toFloat() / totalBytes).coerceIn(0f, 0.99f) else 0f)
                    }
                    input.reader(Charsets.UTF_8).buffered(256 * 1024).use { reader ->
                        M3uParser.parse(reader, { url -> isVodEntry(url) }) { entry ->
                            if (vodBudget.getAndDecrement() > 0) {
                                val number = sourceMovies.size + 1
                                sourceMovies += VodItem(
                                    key = source.id + ":" + stableId("vod|${entry.url}|$number"),
                                    sourceId = source.id,
                                    sourceName = source.name,
                                    name = entry.name,
                                    logo = entry.logo,
                                    group = entry.group?.ifBlank { null } ?: "On demand",
                                    number = number,
                                    url = entry.url,
                                    headers = entry.headers,
                                    drm = entry.drm,
                                    durationSeconds = entry.durationSeconds,
                                )
                            }
                        }
                    }
                }
                val errors = ArrayList<String>()
                val channels = playlist.entries.mapIndexed { index, entry ->
                    LiveChannel(
                        // Same key scheme as before, so favourites/recordings stay attached.
                        key = source.id + ":" + stableId("${entry.tvgId}|${entry.url}|$index"),
                        sourceId = source.id,
                        sourceName = source.name,
                        name = entry.name,
                        tvgId = entry.tvgId,
                        tvgName = entry.tvgName,
                        logo = entry.logo,
                        group = entry.group?.ifBlank { null } ?: "Other",
                        number = entry.chno ?: index + 1,
                        url = entry.url,
                        headers = entry.headers,
                        catchup = entry.catchup,
                        drm = entry.drm,
                    )
                }
                if (channels.isEmpty() && source.kind.equals("xtream", true)) {
                    val apiChannels = runCatching { xtreamApiChannels(source) }.getOrNull().orEmpty()
                    if (apiChannels.isNotEmpty()) {
                        return SourceResult(apiChannels, sourceMovies, playlist.epgUrls.toList(), emptyList())
                    }
                }
                if (channels.isEmpty()) {
                    errors += "${source.name}: the link returned no channels. It may be a login page, " +
                        "an HTML page, or a redirect the provider only allows in its own app."
                }
                return SourceResult(channels, sourceMovies, playlist.epgUrls.toList(), errors)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                if (attempt == 0 && e is IOException) delay(900) else break
            }
        }
        if (source.kind.equals("xtream", true)) {
            val apiChannels = runCatching { xtreamApiChannels(source) }.getOrNull().orEmpty()
            if (apiChannels.isNotEmpty()) {
                return SourceResult(apiChannels, emptyList(), emptyList(), emptyList())
            }
        }
        return SourceResult(
            emptyList(),
            emptyList(),
            emptyList(),
            listOf("${source.name}: ${safeError(lastError ?: IOException("Playlist download failed"))}"),
        )
    }

    /**
     * A Stalker / Ministra portal: the channel list and guide come from the portal itself; the saved
     * channel URL is a `stalker://` pseudo link and is resolved to a temporary stream just before playback.
     */
    private suspend fun loadStalkerSource(
        source: IptvSource,
        onFraction: (Float) -> Unit,
    ): SourceResult {
        if (!StalkerClient.isValidMac(source.username)) {
            return SourceResult(
                emptyList(), emptyList(), emptyList(),
                listOf("${source.name}: enter a valid MAC address for the portal."),
            )
        }
        return runCatching {
            onFraction(0.05f)
            val client = StalkerClient(source.url, source.username, StalkerLinks.http)
            val genres = runCatching { client.genres() }.getOrDefault(emptyMap())
            onFraction(0.15f)
            val channels = client.channels()
            onFraction(0.55f)
            val guide = client.guide(24)
            onFraction(0.9f)

            val live = channels.map { ch ->
                LiveChannel(
                    key = source.id + ":" + stableId("stalker|${ch.id}"),
                    sourceId = source.id,
                    sourceName = source.name,
                    name = ch.name,
                    tvgId = StalkerClient.tvgId(ch),
                    tvgName = ch.name,
                    logo = ch.logo,
                    group = genres[ch.genreId]?.ifBlank { null } ?: "Stalker",
                    number = ch.number,
                    url = StalkerClient.channelUrl(source.id, ch),
                    headers = emptyMap(),
                    catchup = null,
                    drm = null,
                )
            }
            val programs = buildMap<String, List<EpgProgram>> {
                channels.forEach { ch ->
                    val list = guide[ch.id].orEmpty().map {
                        EpgProgram(
                            startMs = it.startSec * 1000,
                            stopMs = it.stopSec * 1000,
                            title = it.title,
                            description = it.description,
                        )
                    }
                    if (list.isNotEmpty()) put(StalkerClient.tvgId(ch), list)
                }
            }
            onFraction(1f)
            SourceResult(
                channels = live,
                movies = emptyList(),
                epgUrls = emptyList(),
                errors = if (live.isEmpty()) {
                    listOf("${source.name}: the portal returned no channels.")
                } else {
                    emptyList()
                },
                programs = programs,
            )
        }.getOrElse { e ->
            SourceResult(
                emptyList(), emptyList(), emptyList(),
                listOf("${source.name}: ${safeError(e as? Exception ?: Exception(e))}"),
            )
        }
    }

    /**
     * Xtream's own JSON API: live streams + their categories. Much smaller and
     * faster than get.php's m3u_plus. Used only as a preview until the full
     * playlist arrives, so its keys are separate from the real channel keys.
     */
    private fun xtreamLivePreview(source: IptvSource): List<LiveChannel> {
        fun fetch(action: String): kotlinx.serialization.json.JsonArray? = open(xtreamUrl(source, "player_api.php", "action=$action"))
            .use { input -> input.reader(Charsets.UTF_8).readText() }
            .let { text -> runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) }.getOrNull() }
            as? kotlinx.serialization.json.JsonArray

        fun kotlinx.serialization.json.JsonElement.str(key: String): String? =
            ((this as? kotlinx.serialization.json.JsonObject)?.get(key) as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.takeIf { it.isNotBlank() && it != "null" }

        val categories = fetch("get_live_categories").orEmpty()
            .mapNotNull { c -> c.str("category_id")?.let { id -> id to (c.str("category_name") ?: "Other") } }
            .toMap()
        val streams = fetch("get_live_streams") ?: return emptyList()
        val base = source.url.trim().trimEnd('/')
        fun encoded(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
        return streams.mapIndexedNotNull { index, s ->
            val id = s.str("stream_id") ?: return@mapIndexedNotNull null
            val url = "$base/live/${encoded(source.username)}/${encoded(source.password)}/$id.ts"
            LiveChannel(
                key = source.id + ":preview:" + stableId(url),
                sourceId = source.id,
                sourceName = source.name,
                name = s.str("name") ?: "Channel $id",
                tvgId = s.str("epg_channel_id"),
                tvgName = null,
                logo = s.str("stream_icon"),
                group = s.str("category_id")?.let(categories::get) ?: "Other",
                number = s.str("num")?.toIntOrNull() ?: index + 1,
                url = url,
                headers = emptyMap(),
                catchup = null,
                drm = null,
            )
        }
    }

    /**
     * Loads live channels through Xtream's player_api.php. Some providers block the one-shot
     * get.php playlist (this one answers HTTP 884), while the API stays available; TiviMate and
     * the TV app use the API for the same reason. Keys are stable across loads.
     */
    private fun xtreamApiChannels(source: IptvSource): List<LiveChannel> {
        fun element(text: String) = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(text)
        }.getOrNull()

        fun fetchObject(url: String) =
            open(url).use { it.reader(Charsets.UTF_8).readText() }.let(::element)
                as? kotlinx.serialization.json.JsonObject

        fun fetchArray(action: String) = open(xtreamUrl(source, "player_api.php", "action=$action"))
            .use { it.reader(Charsets.UTF_8).readText() }
            .let(::element) as? kotlinx.serialization.json.JsonArray

        fun kotlinx.serialization.json.JsonElement.str(key: String): String? =
            ((this as? kotlinx.serialization.json.JsonObject)?.get(key) as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.takeIf { it.isNotBlank() && it != "null" }

        val base = cleanXtreamServer(source.url)
        require(base.startsWith("http://") || base.startsWith("https://")) { "Xtream server must be HTTP(S)" }
        fun encoded(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
        val api = "$base/player_api.php?username=${encoded(source.username)}&password=${encoded(source.password)}"
        val account = fetchObject(api) ?: error("Not an Xtream API response")
        val user = account["user_info"] as? kotlinx.serialization.json.JsonObject
        if (user?.get("auth")?.jsonPrimitive?.content == "0") {
            error("Login failed: check the username and password")
        }
        val status = user?.get("status")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        if (status != null && !status.equals("Active", ignoreCase = true)) error("Account $status")
        val allowed = (user?.get("allowed_output_formats") as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it.jsonPrimitive.content?.lowercase() }
            ?.toSet()
            .orEmpty()
        val extension = if (allowed.isNotEmpty() && "ts" !in allowed && "m3u8" in allowed) "m3u8" else "ts"

        val categories = fetchArray("get_live_categories").orEmpty()
            .mapNotNull { c -> c.str("category_id")?.let { id -> id to (c.str("category_name") ?: "Other") } }
            .toMap()
        val streams = fetchArray("get_live_streams") ?: error("The Xtream API returned no live streams")
        return streams.mapIndexedNotNull { index, s ->
            val id = s.str("stream_id") ?: return@mapIndexedNotNull null
            val archiveDays = s.str("tv_archive_duration")?.toIntOrNull()?.takeIf { it > 0 }
            val catchup = if (s.str("tv_archive") == "1" && archiveDays != null) {
                CatchupInfo(type = "xtream", source = null, days = archiveDays)
            } else {
                null
            }
            LiveChannel(
                key = source.id + ":xtream:" + stableId("xtream|$id"),
                sourceId = source.id,
                sourceName = source.name,
                name = s.str("name") ?: "Channel $id",
                tvgId = s.str("epg_channel_id"),
                tvgName = null,
                logo = s.str("stream_icon"),
                group = s.str("category_id")?.let(categories::get) ?: "Other",
                number = s.str("num")?.toIntOrNull() ?: index + 1,
                url = "$base/live/${encoded(source.username)}/${encoded(source.password)}/$id.$extension",
                headers = emptyMap(),
                catchup = catchup,
                drm = null,
            )
        }
    }

    private fun open(rawUrl: String, onLength: (Long) -> Unit = {}): InputStream {
        val uri = URI.create(normalizePlaylistUrl(rawUrl))
        require(uri.scheme?.lowercase() in setOf("http", "https", "file")) { "Use an HTTP(S) or file URL" }
        if (uri.scheme.equals("file", ignoreCase = true)) {
            runCatching { Files.size(Path.of(uri)) }.getOrNull()?.let(onLength)
            val input = BufferedInputStream(uri.toURL().openStream())
            return if (uri.path?.endsWith(".gz", ignoreCase = true) == true) GZIPInputStream(input) else input
        }

        var current = uri
        val visited = LinkedHashSet<String>()
        for (hop in 0..MAX_REDIRECTS) {
            if (!visited.add(current.toString())) error("Redirect loop")
            val connection = current.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept-Encoding", "gzip")
            connection.setRequestProperty("User-Agent", "NuvioOneDesktop/0.1")
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) error("HTTP $status without a Location header")
                current = runCatching { current.resolve(location.trim()) }
                    .getOrElse { error("HTTP $status with an invalid Location header") }
                require(current.scheme?.lowercase() in setOf("http", "https")) {
                    "Redirected to an unsupported URL"
                }
                continue
            }
            if (status !in 200..299) {
                connection.disconnect()
                error("HTTP $status")
            }
            connection.contentLengthLong.takeIf { it > 0 }?.let(onLength)
            val input = BufferedInputStream(connection.getInputStream())
            val gzip = current.path?.endsWith(".gz", ignoreCase = true) == true ||
                connection.contentEncoding.equals("gzip", true)
            return if (gzip) GZIPInputStream(input) else input
        }
        error("Too many redirects")
    }

    private class CountingInputStream(
        input: InputStream,
        private val onBytes: (Long) -> Unit,
    ) : FilterInputStream(input) {
        private var read = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                read += 1
                onBytes(read)
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) {
                read += count
                onBytes(read)
            }
            return count
        }
    }

    private fun xtreamUrl(source: IptvSource, endpoint: String, extra: String = ""): String {
        val base = cleanXtreamServer(source.url)
        require(base.startsWith("http://") || base.startsWith("https://")) { "Xtream server must be HTTP(S)" }
        fun encoded(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
        val query = "username=${encoded(source.username)}&password=${encoded(source.password)}"
        return "$base/$endpoint?$query${if (extra.isEmpty()) "" else "&$extra"}"
    }

    private val digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
    private val hexDigits = "0123456789abcdef".toCharArray()

    /** First 8 bytes of SHA-256 as hex; same output as before, without a new digest per entry. */
    /** One entry per playlist address + login, keeping the first and any guide link from the others. */
    internal fun distinctSources(sources: List<IptvSource>): List<IptvSource> {
        val groups = LinkedHashMap<String, MutableList<IptvSource>>()
        sources.forEach { groups.getOrPut(sameSourceKey(it)) { ArrayList() } += it }
        return groups.values.map { same ->
            val first = same.first()
            if (first.epgUrl.isNotBlank()) first
            else first.copy(epgUrl = same.firstOrNull { it.epgUrl.isNotBlank() }?.epgUrl.orEmpty())
        }
    }

    /** Sources that would download the same playlist. */
    internal fun sameSourceKey(s: IptvSource): String =
        listOf(
            s.kind.lowercase(),
            s.url.trim().trimEnd('/').lowercase(),
            s.username.trim(),
            s.password,
        ).joinToString("\u0001")

    private fun stableId(value: String): String {
        val bytes = digest.get().digest(value.toByteArray(StandardCharsets.UTF_8))
        val out = CharArray(16)
        for (i in 0 until 8) {
            val b = bytes[i].toInt() and 0xff
            out[i * 2] = hexDigits[b ushr 4]
            out[i * 2 + 1] = hexDigits[b and 0x0f]
        }
        return String(out)
    }

    private fun safeError(error: Exception): String = when {
        error is SocketTimeoutException -> "Timed out while downloading"
        error is UnknownHostException -> "Server not found"
        error.message?.matches(Regex("HTTP [0-9]{3}")) == true -> error.message!!
        error is IllegalArgumentException -> "Invalid source URL"
        error is IOException -> "Network error: ${error.javaClass.simpleName}"
        else -> error.javaClass.simpleName
    }
}
