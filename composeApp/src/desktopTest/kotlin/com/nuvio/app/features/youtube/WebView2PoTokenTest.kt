package com.nuvio.app.features.youtube

import com.liskovsoft.sharedutils.platform.EngineContext
import com.liskovsoft.sharedutils.platform.EnginePlatform
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.app.potokennp2.misc.PoTokenRuntime
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebView2PoTokenTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            assumeTrue(System.getProperty("os.name").lowercase().contains("win"))
            val root = Files.createTempDirectory("nuvio-potoken-test")
            EnginePlatform.configure(root.resolve("files"), root.resolve("cache"))
            GlobalPreferences.instance(EngineContext.get())
        }
    }

    @Test(timeout = 60_000)
    fun hiddenWebView2ExecutesScriptsAndCallbacks() {
        val runtime = WebView2PoTokenJsRuntime()
        try {
            val result = runtime.executeStringScript("JSON.stringify({ value: 1 + 1 })")
            assertEquals("{\"value\":2}", result)

            val latch = CountDownLatch(1)
            var received: List<Any?>? = null
            runtime.registerCallback("nuvioTestCallback") { args ->
                received = args
                latch.countDown()
            }
            runtime.executeVoidScript("nuvioTestCallback(7, 'hello')")

            assertTrue(latch.await(5, TimeUnit.SECONDS), "callback did not fire")
            assertEquals(7.0, received?.getOrNull(0))
            assertEquals("hello", received?.getOrNull(1))
        } finally {
            runtime.shutdownRuntime()
        }
    }

    @Test(timeout = 180_000)
    fun generatesRealPoToken() {
        PoTokenRuntime.hostFactory = { WebView2PoTokenJsRuntime() }
        try {
            val token = PoTokenRuntime.generateToken("dQw4w9WgXcQ")
            assertNotNull(token, "poToken is null")
            assertTrue(token.isNotBlank(), "poToken is blank")
            println("WebView2 poToken length=${token.length}")
        } finally {
            PoTokenRuntime.hostFactory = null
        }
    }

    @Test(timeout = 240_000)
    fun webClientUsesPoTokenToFetchFormats() {
        PoTokenRuntime.hostFactory = { WebView2PoTokenJsRuntime() }
        try {
            val service = com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoService.instance()
            val info = service.getVideoInfoForClient(
                com.liskovsoft.youtubeapi.common.helpers.AppClient.WEB,
                "dQw4w9WgXcQ",
                null,
            )
            assertNotNull(info, "WEB video info is null")
            println(
                "Served by ${info.client} formats=${info.adaptiveFormats?.size ?: 0} " +
                    "status=${info.playabilityStatus}"
            )
            assertEquals(
                com.liskovsoft.youtubeapi.common.helpers.AppClient.WEB,
                info.client,
                "WEB client did not serve the response",
            )
            assertTrue(
                (info.adaptiveFormats?.size ?: 0) > 0 || info.hlsManifestUrl != null,
                "WEB response has no playable formats",
            )
        } finally {
            PoTokenRuntime.hostFactory = null
        }
    }
}
