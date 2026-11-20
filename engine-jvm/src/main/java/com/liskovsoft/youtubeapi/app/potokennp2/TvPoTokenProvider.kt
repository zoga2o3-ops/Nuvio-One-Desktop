package com.liskovsoft.youtubeapi.app.potokennp2

import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.youtubeapi.app.AppService
import com.liskovsoft.youtubeapi.app.potokennp2.core.PoTokenGenerator
import com.liskovsoft.youtubeapi.app.potokennp2.core.PoTokenResult
import com.liskovsoft.youtubeapi.app.potokennp2.generators.PoTokenV8
import com.liskovsoft.youtubeapi.app.potokennp2.misc.runOnMainThreadSync

/**
 * The living room (TV) client has its own BotGuard request key and challenge (tv_config).
 * Its poToken is a session one: it is bound to the living room token id (the /tv page)
 * and not to the video id.
 */
internal object TvPoTokenProvider {
    private val TAG = TvPoTokenProvider::class.simpleName
    private val lock = Any()
    private const val FORCE_ATT_CHALLENGE = true
    private var generator: PoTokenGenerator? = null
    private var result: PoTokenResult? = null
    @Volatile
    private var expirationMs: Long = -1

    /**
     * The id that should be passed to the player request: tvAppInfo.livingRoomPoTokenId
     */
    var livingRoomPoTokenId: String? = null
        private set

    fun isPotExpired(): Boolean = System.currentTimeMillis() > expirationMs

    fun getPoToken(): PoTokenResult? {
        if (!WebPoTokenProvider.isPotSupported()) {
            return null
        }

        return try {
            synchronized(lock) { getPoTokenSync() }
        } catch (e: Exception) {
            Log.e(TAG, "Could not obtain TV poToken", e)
            reset()
            null
        }
    }

    /** Clears all state, including the generator and cached tokens. The next call regenerates everything. */
    fun reset() {
        synchronized(lock) {
            result = null
            livingRoomPoTokenId = null
            expirationMs = -1
            release()
        }
    }

    /** Frees the generator to reduce memory. Cached values are kept; the generator is recreated on demand. */
    fun release() {
        synchronized(lock) {
            val old = generator
            generator = null
            old?.let { runOnMainThreadSync(3) { it.close() } }
        }
    }

    private fun getPoTokenSync(): PoTokenResult? {
        result?.let { if (generator != null && !isPotExpired()) return it }

        // The id and the visitor data should come from the same /tv page
        val appInfo = AppService.instance().getAppInfo(DefaultHeaders.APP_USER_AGENT)
        val livingRoomId = appInfo?.livingRoomPoTokenId
        val visitorData = appInfo?.visitorData

        if (livingRoomId == null || visitorData == null) {
            Log.e(TAG, "The /tv page doesn't contain the living room token id or the visitor data")
            return null
        }

        reset()

        val newGenerator = PoTokenV8.newPoTokenGenerator(AppService.instance().context)
        val sessionPot = newGenerator.generatePoToken(livingRoomId)

        // Log.d(TAG, "TV poToken: livingRoomId=$livingRoomId, pot=$sessionPot, visitor_data=$visitorData")

        generator = newGenerator
        expirationMs = newGenerator.expirationMs
        livingRoomPoTokenId = livingRoomId
        // Same session token is used both for the player request and the streaming
        result = PoTokenResult("", visitorData, sessionPot, sessionPot)

        return result
    }
}