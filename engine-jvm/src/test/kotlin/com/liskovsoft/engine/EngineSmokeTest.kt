package com.liskovsoft.engine

import com.liskovsoft.sharedutils.platform.EngineContext
import com.liskovsoft.sharedutils.platform.EnginePlatform
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Network smoke test for the ported SmartTube engine.
 *
 * Run with: gradlew :engine-jvm:test --tests "*EngineSmokeTest*"
 */
class EngineSmokeTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initEngine() {
            val root = Path.of(System.getProperty("java.io.tmpdir"), "nuvio-engine-smoke")
            EnginePlatform.configure(root.resolve("files"), root.resolve("cache"))
            GlobalPreferences.instance(EngineContext.get())
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    fun searchYouTubeAndResolveStream() {
        val manager = YouTubeServiceManager.instance()

        val groups = manager.contentService.getSearch("lofi hip hop")
        assertTrue(groups.isNotEmpty(), "search returned no groups")

        val items = groups.flatMap { it.mediaItems ?: emptyList() }
        assertTrue(items.isNotEmpty(), "search returned no media items")

        val video = items.firstOrNull { !it.videoId.isNullOrBlank() }
        assertNotNull(video, "search returned no playable video")

        println("Search hit: ${video.title} (${video.videoId})")

        val info = manager.mediaItemService.getFormatInfo(video.videoId)
        assertNotNull(info, "format info is null")

        val adaptive = info.adaptiveFormats?.size ?: 0
        val urlFormats = info.urlFormats?.size ?: 0
        println(
            "Formats: adaptive=$adaptive url=$urlFormats " +
                "hls=${info.hlsManifestUrl != null} dash=${info.dashManifestUrl != null} " +
                "unplayable=${info.isUnplayable} reason=${info.playabilityReason}"
        )

        val firstAdaptive = info.adaptiveFormats?.firstOrNull()
        if (firstAdaptive != null) {
            println("First adaptive: mime=${firstAdaptive.mimeType} itag=${firstAdaptive.iTag} url=${firstAdaptive.url != null}")
        }

        val userAgent = info.clientInfo?.userAgent
            ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        val probe = info.adaptiveFormats
            ?.filter { it.mimeType?.startsWith("video/") == true && !it.url.isNullOrBlank() }
            ?.maxByOrNull { it.height }
            ?.url
            ?: info.hlsManifestUrl

        assertNotNull(probe, "no stream URL to probe")
        probeStream(probe, userAgent)

        assertTrue(
            adaptive > 0 || urlFormats > 0 || info.hlsManifestUrl != null || info.dashManifestUrl != null,
            "no playable formats resolved"
        )
    }

    private fun probeStream(url: String, userAgent: String) {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "https://www.youtube.com/")
            .header("Range", "bytes=0-1023")
            .build()

        client.newCall(request).execute().use { response ->
            println("Stream probe: HTTP ${response.code} bytes=${response.body?.contentLength()}")
            assertTrue(
                response.isSuccessful || response.code == 206,
                "stream URL probe failed with HTTP ${response.code}",
            )
        }
    }

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    fun homeMetadataAndComments() {
        val manager = YouTubeServiceManager.instance()
        val content = manager.contentService

        report("home") { content.home }
        report("recommended") { listOfNotNull(content.recommended) }
        report("trending") { content.getTrendingObserve().blockingFirst() }
        report("music") { content.getMusicObserve().blockingFirst() }
        report("news") { content.getNewsObserve().blockingFirst() }
        report("gaming") { content.getGamingObserve().blockingFirst() }
        report("sports") { content.getSportsObserve().blockingFirst() }
        runCatching { content.subscriptions }.onSuccess {
            println("subscriptions: ${it?.mediaItems?.size ?: 0} items")
        }.onFailure {
            println("subscriptions failed: ${it.message}")
        }

        val metadata = manager.mediaItemService.getMetadata("dQw4w9WgXcQ")
        assertNotNull(metadata, "metadata is null")
        println(
            "Metadata: title=${metadata.title} author=${metadata.author} " +
                "suggestions=${metadata.suggestions?.size ?: 0} " +
                "commentsKey=${metadata.commentsKey != null} liveChatKey=${metadata.liveChatKey != null}"
        )

        metadata.commentsKey?.let { key ->
            val comments = runCatching {
                manager.commentsService.getCommentsObserve(key).blockingFirst()
            }
            comments.onSuccess {
                println("Comments: ${it.comments?.size ?: 0} first=${it.comments?.firstOrNull()?.message?.take(80)}")
            }.onFailure {
                println("Comments failed: ${it.message}")
            }
        }
    }

    private fun report(name: String, block: () -> List<com.liskovsoft.mediaserviceinterfaces.data.MediaGroup>?) {
        runCatching { block() }.onSuccess { groups ->
            val items = groups.orEmpty().sumOf { it.mediaItems?.size ?: 0 }
            println("$name: ${groups?.size ?: 0} groups, $items items")
        }.onFailure {
            println("$name failed: ${it.message}")
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun tvSignInEmitsUserCode() {
        val manager = YouTubeServiceManager.instance()
        val latch = java.util.concurrent.CountDownLatch(1)
        val code = java.util.concurrent.atomic.AtomicReference<String?>()

        val disposable = manager.signInService.signInObserve().subscribe(
            {
                code.set(it)
                latch.countDown()
            },
            { println("Sign-in error: ${it.message}") },
        )

        val received = latch.await(30, TimeUnit.SECONDS)
        disposable.dispose()

        assertTrue(received, "TV sign-in did not emit a user code")
        assertTrue(!code.get().isNullOrBlank(), "TV sign-in emitted an empty user code")
        println("TV sign-in user code received (length=${code.get()!!.length})")
    }

    @org.junit.jupiter.api.Disabled(
        "GraalJS BotGuard environment is incomplete; TV clients do not require a poToken"
    )
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    fun potokenV8GeneratesToken() {
        YouTubeServiceManager.instance() // initializes engine context
        val result = com.liskovsoft.youtubeapi.app.potokennp2.WebPoTokenProvider
            .getPoToken("dQw4w9WgXcQ")
        assertNotNull(result, "poToken result is null")
        println(
            "PoToken: playerLength=${result.playerRequestPoToken?.length ?: 0} " +
                "streamingLength=${result.streamingDataPoToken?.length ?: 0} " +
                "visitorLength=${result.visitorData?.length ?: 0}"
        )
        assertTrue(!result.playerRequestPoToken.isNullOrBlank(), "player poToken is empty")
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun v8TimerBridge() {
        val wrapper = com.liskovsoft.youtubeapi.app.potokennp2.misc.V8Wrapper()
        val polyfill = javaClass.classLoader!!.getResourceAsStream("potokennp2/v8/polyfill.js")!!
            .bufferedReader().use { it.readText() }

        wrapper.registerJavaMethod({ _, args ->
            println("nativeSetInterval delay=${args.getInteger(0)}")
            7
        }, "__nativeSetInterval")
        wrapper.registerJavaMethod({ _, args ->
            println("nativeClearInterval id=${args.getInteger(0)}")
        }, "__nativeClearInterval")

        wrapper.executeVoidScript(polyfill)
        wrapper.executeVoidScript(
            """
            globalThis.__fired = 0;
            globalThis.__timerId = setInterval(function () { globalThis.__fired += 1; }, 1);
            """.trimIndent()
        )
        Thread.sleep(2500)
        val result = wrapper.executeStringScript("String(globalThis.__fired) + ':' + String(globalThis.__timerId)")
        println("Timer bridge result=$result")
        wrapper.shutdownRuntime()
        assertTrue(result != "0:undefined", "setInterval bridge did not fire")
    }
}
