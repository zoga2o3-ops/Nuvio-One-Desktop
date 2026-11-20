package com.nuvio.app.features.youtube

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.liskovsoft.youtubeapi.app.potokennp2.misc.PoTokenJsRuntime
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the SmartTube BotGuard scripts inside a hidden WebView2.
 *
 * The engine still owns the BotGuard protocol (challenge request, GenerateIT,
 * token encoding). This class is only the JS runtime: it evaluates scripts and
 * forwards callbacks posted by the page.
 */
internal class WebView2PoTokenJsRuntime : PoTokenJsRuntime {
    private val callbacks = ConcurrentHashMap<String, (List<Any?>) -> Unit>()
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nuvio-potoken-dispatch").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private val handle: Long
    private val messagePump: Thread

    init {
        NativePlayerBridge.ensureLoaded()
        handle = NativePlayerBridge.createPoTokenWebView()
        if (handle == 0L) {
            throw IllegalStateException("Could not create the hidden WebView2 poToken session")
        }
        messagePump = Thread({ pumpMessages() }, "nuvio-potoken-messages").apply {
            isDaemon = true
            start()
        }
    }

    override fun executeStringScript(script: String): String? {
        val json = NativePlayerBridge.executePoTokenScript(handle, script) ?: return null
        return unwrapJson(json)
    }

    override fun executeVoidScript(script: String) {
        NativePlayerBridge.executePoTokenScript(handle, script)
    }

    override fun registerCallback(name: String, callback: (List<Any?>) -> Unit) {
        callbacks[name] = callback
        val escaped = name.replace("\\", "\\\\").replace("\"", "\\\"")
        executeVoidScript(
            """
            globalThis["$escaped"] = function() {
                window.chrome.webview.postMessage({
                    name: "$escaped",
                    args: Array.prototype.slice.call(arguments)
                });
            };
            """.trimIndent()
        )
    }

    override fun executeJsFunction(name: String): Any? = executeStringScript("$name()")

    override fun shutdownRuntime() {
        if (!closed.compareAndSet(false, true)) return

        val teardown = Runnable {
            messagePump.interrupt()
            messagePump.join(1500)

            dispatcher.shutdown()
            runCatching { dispatcher.awaitTermination(2, TimeUnit.SECONDS) }

            NativePlayerBridge.destroyPoTokenWebView(handle)
        }

        // Error callbacks run on the dispatcher itself; never join/shutdown it
        // from its own thread.
        if (Thread.currentThread().name == "nuvio-potoken-dispatch") {
            Thread(teardown, "nuvio-potoken-teardown").apply {
                isDaemon = true
                start()
            }
        } else {
            teardown.run()
        }
    }

    private fun pumpMessages() {
        while (!closed.get()) {
            val raw = runCatching {
                NativePlayerBridge.takePoTokenMessage(handle, 500)
            }.getOrNull() ?: continue

            val element = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: continue
            val obj = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: continue
            val name = obj.get("name")?.asString ?: continue
            val args = obj.getAsJsonArray("args")
                ?.map { it.toAny() }
                .orEmpty()

            if (closed.get()) return
            val callback = callbacks[name] ?: continue
            runCatching {
                dispatcher.execute {
                    if (!closed.get()) callback(args)
                }
            }
        }
    }

    private fun unwrapJson(json: String): String? {
        val element = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return null
        return when {
            element.isJsonNull -> null
            element.isJsonPrimitive -> element.asJsonPrimitive.asString
            else -> element.toString()
        }
    }

    private fun JsonElement.toAny(): Any? = when {
        isJsonNull -> null
        isJsonPrimitive -> {
            val primitive = asJsonPrimitive
            when {
                primitive.isString -> primitive.asString
                primitive.isBoolean -> primitive.asBoolean
                else -> primitive.asDouble
            }
        }
        isJsonArray -> asJsonArray.map { it.toAny() }
        isJsonObject -> asJsonObject.entrySet()
            .associate { it.key to it.value.toAny() }
        else -> null
    }
}
