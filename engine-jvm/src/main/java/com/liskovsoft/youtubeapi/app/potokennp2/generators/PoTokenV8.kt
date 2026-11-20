package com.liskovsoft.youtubeapi.app.potokennp2.generators

import android.content.Context
import androidx.annotation.MainThread
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.sharedutils.okhttp.OkHttpManager
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.youtubeapi.app.nsigsolver.common.loadScript
import com.liskovsoft.youtubeapi.app.potokennp2.core.BadWebViewException
import com.liskovsoft.youtubeapi.app.potokennp2.core.PoTokenException
import com.liskovsoft.youtubeapi.app.potokennp2.core.PoTokenGenerator
import com.liskovsoft.youtubeapi.app.potokennp2.core.V8WrapperException
import com.liskovsoft.youtubeapi.app.potokennp2.core.buildExceptionForJsError
import com.liskovsoft.youtubeapi.app.potokennp2.misc.PoTokenJsRuntime
import com.liskovsoft.youtubeapi.app.potokennp2.misc.PoTokenRuntime
import com.liskovsoft.youtubeapi.app.potokennp2.misc.hasThermalServiceBug
import com.liskovsoft.youtubeapi.app.potokennp2.misc.hasUsbServiceBug
import com.liskovsoft.youtubeapi.app.potokennp2.misc.parseDescrambledChallengeData
import com.liskovsoft.youtubeapi.app.potokennp2.misc.parseIntegrityTokenData
import com.liskovsoft.youtubeapi.app.potokennp2.misc.potLibPrefix
import com.liskovsoft.youtubeapi.app.potokennp2.misc.stringToU8
import com.liskovsoft.youtubeapi.app.potokennp2.misc.u8ToBase64
import com.liskovsoft.youtubeapi.common.helpers.AppClient
import io.reactivex.SingleEmitter
import io.reactivex.disposables.Disposable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * TODO: remove me later. It's impossible to build full fledged browser-like environment using the V8 engine
 */
internal class PoTokenV8 private constructor(
    context: Context,
    private var onInitDone: () -> Unit
) : PoTokenGenerator {
    private val jsRuntime: PoTokenJsRuntime = PoTokenRuntime.create()
    private val poTokenEmitters = mutableListOf<Pair<String, (String) -> Unit>>()
    private var initError: Throwable? = null
    private val v8NpmLibFilenames =
        listOf(
            "${potLibPrefix}v8/polyfill.js",
            //"${potLibPrefix}v8/linkedom.bundle.js",
            "${potLibPrefix}v8/bootstrap.js",
            "${potLibPrefix}v8/po_token2.js"
        )
    override var expirationMs: Long = -1
        private set

    //region Initialization
    init {
        initPolyfills()

        // load common libs: jsdom, polyfill
        jsRuntime.registerCallback("onRunBotguardResult") { args ->
            onRunBotguardResult(args.stringAt(0))
        }
        jsRuntime.registerCallback("onJsInitializationError") { args ->
            onJsInitializationError(args.stringAt(0))
        }
        jsRuntime.registerCallback("onObtainPoTokenResult") { args ->
            onObtainPoTokenResult(args.stringAt(0), args.stringAt(1))
        }
        jsRuntime.registerCallback("onObtainPoTokenError") { args ->
            onObtainPoTokenError(args.stringAt(0), args.stringAt(1))
        }
        jsRuntime.registerCallback("onJsInitializationDone") { args ->
            onJsInitializationDone(args.intAt(0))
        }
    }

    private fun initPolyfills() {
        val disposables = ConcurrentHashMap<Int, Disposable>()

        jsRuntime.registerCallback("__nativeSetInterval") { args ->
            val id = args.intAt(0)
            val delay = args.intAt(1)

            val disposable = RxHelper.startIntervalMs({
                jsRuntime.executeVoidScript("globalThis.__runInterval($id)")
            }, delay)

            disposables[id] = disposable
        }

        jsRuntime.registerCallback("__nativeClearInterval") { args ->
            disposables.remove(args.intAt(0))?.dispose()
        }
    }

    /**
     * Must be called right after instantiating [PoTokenV8] to perform the actual
     * initialization. This will asynchronously go through all the steps needed to load BotGuard,
     * run it, and obtain an `integrityToken`.
     */
    private fun loadScriptAndObtainBotguard() {
        Log.d(TAG, "loadHtmlAndObtainBotguard() called")

        jsRuntime.executeVoidScript(loadScript(v8NpmLibFilenames))
        downloadAndRunBotguard()
    }

    /**
     * Called during initialization by the JavaScript snippet appended to the HTML page content in
     * [loadScriptAndObtainBotguard] after the WebView content has been loaded.
     */
    private fun downloadAndRunBotguard() {
        Log.d(TAG, "downloadAndRunBotguard() called")

        // PATCH(unstem 2026-08): always mint from the homepage's (ytcfg, ytAtN)
        // pair. Legacy /att/get challenges are rejected because BotGuard reads
        // yt.config_.EVENT_ID.
        val (parsedChallengeData, ytcfg) =
            getChallengeFromHomepage() ?: getLegacyChallengeData() ?: return

        jsRuntime.executeVoidScript(
            """
                try {
                    if ($ytcfg)
                        yt = { config_: $ytcfg };
                    runBotGuard($parsedChallengeData).then(function (result) {
                        webPoSignalOutput = result.webPoSignalOutput;
                        if (!webPoSignalOutput.length) {
                            onJsInitializationError("webPoSignalOutput is empty");
                        } else {
                            onRunBotguardResult(result.botguardResponse);
                        }
                    }, function (error) {
                        onJsInitializationError(error + "\n" + error.stack);
                    });
                } catch (error) {
                    onJsInitializationError(error + "\n" + error.stack);
                }
            """
        )
    }

    private fun getChallengeFromHomepage(): Pair<String, String?>? {
        val pageHtml = makeBotguardServiceRequest(
            "https://www.youtube.com",
            null,
            mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.7",
                "user-agent" to USER_AGENT,
            )
        ) ?: return null

        val ytcfg = extractYtCfg(pageHtml)
        val rawChallengeData = extractYtAtN(pageHtml)
        Log.d(
            TAG,
            "homepage challenge: ytcfg=${ytcfg != null} eventId=${ytcfg?.contains("EVENT_ID") == true} " +
                "attLength=${rawChallengeData?.length ?: 0}",
        )
        if (rawChallengeData == null) {
            Log.w(TAG, "homepage-challenge: no ytAtN challenge in page")
            return null
        }

        val challengePayload = extractJsonStringField(rawChallengeData, "R")
        if (challengePayload == null ||
            !challengePayload.contains("bgChallenge") ||
            !challengePayload.contains("program") ||
            !challengePayload.contains("interpreterUrl")
        ) {
            Log.w(TAG, "homepage-challenge: ytAtN payload missing bgChallenge")
            return null
        }

        Log.d(TAG, "Using challenge from the homepage")
        return Pair(parseDescrambledChallengeData(challengePayload), ytcfg)
    }

    private fun extractYtCfg(pageHtml: String): String? {
        var searchFrom = 0
        var fallback: String? = null
        while (true) {
            val marker = pageHtml.indexOf("ytcfg.set(", searchFrom)
            if (marker < 0) break
            var start = marker + "ytcfg.set(".length
            while (start < pageHtml.length && pageHtml[start].isWhitespace()) start++
            val json = if (start < pageHtml.length && pageHtml[start] == '{') {
                extractBalancedJson(pageHtml, start)
            } else {
                null
            }
            if (json != null) {
                if (json.contains("EVENT_ID")) return json
                if (fallback == null || json.length > fallback.length) fallback = json
            }
            searchFrom = start.coerceAtLeast(marker + 1)
        }
        return fallback
    }

    private fun extractYtAtN(pageHtml: String): String? {
        var searchFrom = 0
        var fallback: String? = null
        while (true) {
            val marker = pageHtml.indexOf("window.ytAtN(", searchFrom)
            if (marker < 0) break
            var start = marker + "window.ytAtN(".length
            while (start < pageHtml.length && pageHtml[start].isWhitespace()) start++
            val json = if (start < pageHtml.length && pageHtml[start] == '{') {
                extractBalancedJson(pageHtml, start)
            } else {
                null
            }
            if (json != null) {
                if (json.contains("bgChallenge")) return json
                if (fallback == null || json.length > fallback.length) fallback = json
            }
            searchFrom = start.coerceAtLeast(marker + 1)
        }
        return fallback
    }

    private fun extractBalancedJson(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val ch = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun extractJsonStringField(jsObject: String, field: String): String? {
        val marker = "'$field':"
        var markerIndex = jsObject.indexOf(marker)
        var valueStart = if (markerIndex >= 0) markerIndex + marker.length else -1
        if (markerIndex < 0) {
            val doubleMarker = "\"$field\":"
            markerIndex = jsObject.indexOf(doubleMarker)
            valueStart = if (markerIndex >= 0) markerIndex + doubleMarker.length else -1
        }
        if (markerIndex < 0) return null

        while (valueStart < jsObject.length && jsObject[valueStart].isWhitespace()) valueStart++
        if (valueStart >= jsObject.length) return null

        val quote = jsObject[valueStart]
        if (quote != '\'' && quote != '"') return null

        val raw = StringBuilder()
        var escaped = false
        for (index in valueStart + 1 until jsObject.length) {
            val ch = jsObject[index]
            if (escaped) {
                raw.append('\\').append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == quote) {
                return decodeJsEscapes(raw.toString())
            } else {
                raw.append(ch)
            }
        }
        return null
    }

    private fun decodeJsEscapes(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch != '\\' || index + 1 >= value.length) {
                result.append(ch)
                index++
                continue
            }

            when (val next = value[index + 1]) {
                'x' -> {
                    val code = value.substringOrNull(index + 2, 2)?.toIntOrNull(16)
                    if (code != null) {
                        result.append(code.toChar())
                        index += 4
                    } else {
                        result.append(ch)
                        index++
                    }
                }
                'u' -> {
                    val code = value.substringOrNull(index + 2, 4)?.toIntOrNull(16)
                    if (code != null) {
                        result.append(code.toChar())
                        index += 6
                    } else {
                        result.append(ch)
                        index++
                    }
                }
                'n' -> { result.append('\n'); index += 2 }
                'r' -> { result.append('\r'); index += 2 }
                't' -> { result.append('\t'); index += 2 }
                'b' -> { result.append('\b'); index += 2 }
                'f' -> { result.append('\u000C'); index += 2 }
                '\\', '\'', '"' -> { result.append(next); index += 2 }
                else -> { result.append(next); index += 2 }
            }
        }
        return result.toString()
    }

    private fun String.substringOrNull(start: Int, length: Int): String? =
        if (start >= 0 && start + length <= this.length) {
            substring(start, start + length)
        } else {
            null
        }

    private fun getLegacyChallengeData(): Pair<String, String?>? {
        val client = AppClient.WEB

        val responseBody = makeBotguardServiceRequest(
            "https://www.youtube.com/youtubei/v1/att/get?prettyPrint=false",
            """
                {
                            context: {
                                client: {
                                    clientName: "${client.clientName}",
                                    clientVersion: "${client.clientVersion}",
                                },
                            },
                            engagementType: "ENGAGEMENT_TYPE_UNBOUND",
                 }
            """,
            mapOf(
                "Content-Type" to "application/json"
            )
        ) ?: return null

        Log.d(TAG, "Using challenge from /att/get (legacy fallback)")
        return Pair(parseDescrambledChallengeData(responseBody), null)
    }

    /**
     * Called during initialization by the JavaScript snippets from either
     * [downloadAndRunBotguard] or [onRunBotguardResult].
     */
    private fun onJsInitializationError(error: String) {
        val msg = "onJsInitializationError: $error"
        Log.e(TAG, msg)
        onInitializationErrorCloseAndCancel(buildExceptionForJsError(msg))
    }

    /**
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard] after
     * obtaining the BotGuard execution output [botguardResponse].
     */
    private fun onRunBotguardResult(botguardResponse: String) {
        Log.d(TAG, "botguardResponse: $botguardResponse")

        val responseBody = makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) ?: return

        Log.d(TAG, "GenerateIT response: $responseBody")
        val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)

        // MOD: backport Instant.now().plusSeconds
        // leave 10 minutes of margin just to be sure
        expirationMs = System.currentTimeMillis() + ((expirationTimeInSeconds - 600) * 1_000)

        jsRuntime.executeVoidScript(
            """
                try {
                    getMinter = webPoSignalOutput[0];
                    mintCallback = getMinter($integrityToken);
                    if (typeof mintCallback === 'undefined') {
                        onJsInitializationError("mintCallback is not defined");
                    } else {
                        onJsInitializationDone($expirationTimeInSeconds);
                    }
                    webPoSignalOutput = null;
                    getMinter = null;
                } catch (error) {
                    onJsInitializationError(error + "\n" + error.stack);
                }
            """
        )
    }

    private fun onJsInitializationDone(expirationTimeInSeconds: Int) {
        Log.d(TAG, "initialization finished, expiration=${expirationTimeInSeconds}s")
        onInitDone()
    }
    //endregion

    //region Obtaining poTokens
    override fun generatePoToken(identifier: String): String {
        Log.d(TAG, "generatePoToken() called with identifier $identifier")
        val latch = CountDownLatch(1)
        var pot: String? = null

        addPoTokenEmitter(identifier) {
            pot = it
            latch.countDown()
        }

        val u8Identifier = stringToU8(identifier)

        jsRuntime.executeVoidScript(
            """
                    try {
                        identifier = "$identifier";
                        u8Identifier = $u8Identifier;
                        poTokenU8 = obtainPoToken(u8Identifier);
                        poTokenU8String = "";
                        for (i = 0; i < poTokenU8.length; i++) {
                            if (i != 0) poTokenU8String += ",";
                            poTokenU8String += poTokenU8[i];
                        }
                        onObtainPoTokenResult(identifier, poTokenU8String);
                        poTokenU8 = null;
                        poTokenU8String = null;
                    } catch (error) {
                        onObtainPoTokenError(identifier, error + "\n" + error.stack);
                    }
                """,
        )

        latch.await(10, TimeUnit.SECONDS)
        initError?.let { throw it }

        return pot ?: throw PoTokenException("Timed out generating a poToken")
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] when an error occurs in calling the
     * JavaScript `obtainPoToken()` function.
     */
    fun onObtainPoTokenError(identifier: String, error: String) {
        val msg = "onObtainPoTokenError: identifier=$identifier error=$error"
        Log.e(TAG, msg)
        onInitializationErrorCloseAndCancel(buildExceptionForJsError(msg))
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] with the original identifier and the
     * result of the JavaScript `obtainPoToken()` function.
     */
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        Log.d(TAG, "Generated poToken (before decoding): identifier=$identifier poTokenU8=$poTokenU8")
        val poToken = u8ToBase64(poTokenU8)

        Log.d(TAG, "Generated poToken: identifier=$identifier poToken=$poToken")
        popPoTokenEmitter(identifier)?.invoke(poToken)
    }

    //endregion

    //region Handling multiple emitters
    /**
     * Adds the ([identifier], [emitter]) pair to the [poTokenEmitters] list. This makes it so that
     * multiple poToken requests can be generated invparallel, and the results will be notified to
     * the right emitters.
     */
    private fun addPoTokenEmitter(identifier: String, emitter: (String) -> Unit) {
        synchronized(poTokenEmitters) {
            poTokenEmitters.add(Pair(identifier, emitter))
        }
    }

    /**
     * Extracts and removes from the [poTokenEmitters] list a [SingleEmitter] based on its
     * [identifier]. The emitter is supposed to be used immediately after to either signal a success
     * or an error.
     */
    private fun popPoTokenEmitter(identifier: String): ((String) -> Unit)? {
        return synchronized(poTokenEmitters) {
            poTokenEmitters.indexOfFirst { it.first == identifier }.takeIf { it >= 0 }?.let {
                poTokenEmitters.removeAt(it).second
            }
        }
    }

    /**
     * Clears [poTokenEmitters] and returns its previous contents. The emitters are supposed to be
     * used immediately after to either signal a success or an error.
     */
    private fun popAllPoTokenEmitters(): List<Pair<String, (String) -> Unit>> {
        return synchronized(poTokenEmitters) {
            val result = poTokenEmitters.toList()
            poTokenEmitters.clear()
            result
        }
    }
    //endregion

    //region Utils

    /**
     * Makes a POST request to [url] with the given [data] by setting the correct headers. Calls
     * [onInitializationErrorCloseAndCancel] in case of any network errors and also if the response
     * does not have HTTP code 200, therefore this is supposed to be used only during
     * initialization. Calls [handleResponseBody] with the response body if the response is
     * successful. The request is performed in the background and a disposable is added to
     * [disposables].
     */
    private fun makeBotguardServiceRequest(
        url: String,
        data: String?,
        headers: Map<String, String> = emptyMap()
    ): String? {
        val allHeaders = mapOf(
            // replace the downloader user agent
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json",
            "Content-Type" to "application/json+protobuf",
            "x-goog-api-key" to GOOGLE_API_KEY,
            "x-user-agent" to "grpc-web-javascript/0.1",
        ) + headers

        val response = if (data == null) {
            OkHttpManager.instance().doGetRequest(
                url,
                mapOf("User-Agent" to USER_AGENT) + headers,
            )
        } else {
            OkHttpManager.instance().doPostRequest(url, allHeaders, data, null)
        }

        val httpCode = response.code

        if (httpCode != 200) {
            onInitializationErrorCloseAndCancel(PoTokenException("Invalid response code: $httpCode"))
        }

        if (response.body == null) {
            onInitializationErrorCloseAndCancel(PoTokenException("Response body is empty. Response code: $httpCode"))
        }

        return response.body?.string()
    }

    /**
     * Handles any error happening during initialization, releasing resources and sending the error
     * to [generatorEmitter].
     */
    private fun onInitializationErrorCloseAndCancel(error: Throwable) {
        initError = error
        popAllPoTokenEmitters()
        close()
        // throw error
        onInitDone()
    }

    /**
     * Releases all [jsRuntime] and [disposables] resources.
     */
    @MainThread
    override fun close() {
        jsRuntime.shutdownRuntime()
    }
    //endregion

    private fun List<Any?>.stringAt(index: Int): String =
        getOrNull(index)?.toString().orEmpty()

    private fun List<Any?>.intAt(index: Int): Int = when (val value = getOrNull(index)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }

    companion object : PoTokenGenerator.Factory {
        private val TAG = PoTokenV8::class.simpleName
        // Public API key used by BotGuard, which has been got by looking at BotGuard requests
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw" // NOSONAR
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36(KHTML, like Gecko)"
        //private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        //    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

        override fun newPoTokenGenerator(context: Context): PoTokenGenerator {
            if (hasThermalServiceBug(context)) {
                throw BadWebViewException("ThermalService isn't available")
            }

            if (hasUsbServiceBug(context)) {
                throw BadWebViewException("Usb service isn't available")
            }

            val latch = CountDownLatch(1)

            val potWv = try {
                PoTokenV8(context) { latch.countDown() }
            } catch (e: Throwable) {
                latch.countDown()
                throw V8WrapperException("${e::class.simpleName}: ${e.message}")
            }
            potWv.loadScriptAndObtainBotguard()

            latch.await(30, TimeUnit.SECONDS)

            potWv.initError?.let { throw it }

            return potWv
        }
    }
}
