package com.nuvio.app.features.youtube

import com.nuvio.app.core.storage.DesktopStorage

/** Desktop-persisted YouTube playback preferences. */
internal object YouTubeSettingsStore {
    private val store = DesktopStorage.store("youtube_settings")

    var autoplay: Boolean
        get() = store.getBoolean("autoplay") ?: true
        set(value) = store.putBoolean("autoplay", value)

    var sponsorSkip: Boolean
        get() = store.getBoolean("sponsor_skip") ?: true
        set(value) = store.putBoolean("sponsor_skip", value)

    var resumePlayback: Boolean
        get() = store.getBoolean("resume_playback") ?: true
        set(value) = store.putBoolean("resume_playback", value)

    var preferredQuality: String
        get() = store.getString("preferred_quality") ?: "Auto"
        set(value) = store.putString("preferred_quality", value)

    var preferredAudioLanguage: String
        get() = store.getString("preferred_audio_language") ?: "Original"
        set(value) = store.putString("preferred_audio_language", value)

    var storyboardPreviews: Boolean
        get() = store.getBoolean("storyboard_previews") ?: true
        set(value) = store.putBoolean("storyboard_previews", value)

    var liveChat: Boolean
        get() = store.getBoolean("live_chat") ?: true
        set(value) = store.putBoolean("live_chat", value)

    // ---- Settings ported from the TV app's OneTube section. Each one is
    // observable, so screens recompose as soon as it changes.

    private fun bool(key: String, default: Boolean) = androidx.compose.runtime.mutableStateOf(store.getBoolean(key) ?: default)
    private fun strings(key: String): List<String> =
        store.getString(key).orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private val headerClockState = bool("header_clock", false)
    var headerClock: Boolean
        get() = headerClockState.value
        set(value) { headerClockState.value = value; store.putBoolean("header_clock", value) }

    private val autoSubtitlesState = bool("auto_subtitles", false)
    /** Start videos with subtitles in [subtitleLanguage] when the video has them. */
    var autoSubtitles: Boolean
        get() = autoSubtitlesState.value
        set(value) { autoSubtitlesState.value = value; store.putBoolean("auto_subtitles", value) }

    private val subtitleLanguageState = androidx.compose.runtime.mutableStateOf(
        store.getString("subtitle_language") ?: java.util.Locale.getDefault().language.ifBlank { "en" },
    )
    var subtitleLanguage: String
        get() = subtitleLanguageState.value
        set(value) { subtitleLanguageState.value = value; store.putString("subtitle_language", value) }

    private val translateCaptionsState = androidx.compose.runtime.mutableStateOf(store.getString("translate_captions").orEmpty())
    /** Language code captions are translated into (YouTube `tlang`), or "" to keep the original track. */
    var translateCaptions: String
        get() = translateCaptionsState.value
        set(value) { translateCaptionsState.value = value; store.putString("translate_captions", value) }

    private val liveChatFontState = androidx.compose.runtime.mutableStateOf(store.getInt("live_chat_font") ?: 13)
    /** Live chat message text size in sp. */
    var liveChatFontSp: Int
        get() = liveChatFontState.value
        set(value) { liveChatFontState.value = value; store.putInt("live_chat_font", value) }

    private val liveChatOpacityState = androidx.compose.runtime.mutableStateOf(store.getInt("live_chat_opacity") ?: 100)
    /** Live chat text opacity, in percent. */
    var liveChatOpacityPercent: Int
        get() = liveChatOpacityState.value
        set(value) { liveChatOpacityState.value = value; store.putInt("live_chat_opacity", value) }

    private val postersPerRowState = androidx.compose.runtime.mutableStateOf(store.getInt("posters_per_row") ?: 0)
    /** Videos across grids; 0 = automatic (fits the window). */
    var postersPerRow: Int
        get() = postersPerRowState.value
        set(value) { postersPerRowState.value = value; store.putInt("posters_per_row", value) }

    // Feeds: what to leave out of the OneTube page (History always shows everything).
    private val hideShortsState = bool("hide_shorts", false)
    var hideShorts: Boolean
        get() = hideShortsState.value
        set(value) { hideShortsState.value = value; store.putBoolean("hide_shorts", value) }

    private val hideWatchedState = bool("hide_watched", false)
    var hideWatched: Boolean
        get() = hideWatchedState.value
        set(value) { hideWatchedState.value = value; store.putBoolean("hide_watched", value) }

    private val hideUpcomingState = bool("hide_upcoming", false)
    var hideUpcoming: Boolean
        get() = hideUpcomingState.value
        set(value) { hideUpcomingState.value = value; store.putBoolean("hide_upcoming", value) }

    private val hiddenWordsState = androidx.compose.runtime.mutableStateOf(strings("hidden_words"))
    var hiddenWords: List<String>
        get() = hiddenWordsState.value
        set(value) {
            val clean = value.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            hiddenWordsState.value = clean
            store.putString("hidden_words", clean.joinToString("\n"))
        }

    private val hiddenChannelsState = androidx.compose.runtime.mutableStateOf(strings("hidden_channels"))
    var hiddenChannels: List<String>
        get() = hiddenChannelsState.value
        set(value) {
            val clean = value.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            hiddenChannelsState.value = clean
            store.putString("hidden_channels", clean.joinToString("\n"))
        }

    /** True when the item should be left out of OneTube's feeds. */
    fun hides(item: YouTubeVideoItem): Boolean {
        if (hideShorts && item.isShorts) return true
        if (hideWatched && item.percentWatched >= 90) return true
        if (hideUpcoming) {
            val tags = listOfNotNull(item.badgeText, item.metadata).joinToString(" ").lowercase()
            if ("upcoming" in tags || "premieres" in tags || "scheduled" in tags) return true
        }
        val channel = item.author?.substringBefore(" • ")?.trim().orEmpty()
        if (channel.isNotEmpty() && hiddenChannels.any { it.equals(channel, ignoreCase = true) }) return true
        if (hiddenWords.isNotEmpty()) {
            val text = (item.title + " " + channel).lowercase()
            if (hiddenWords.any { it.lowercase() in text }) return true
        }
        return false
    }

    // Tabs: which show, their order, and whether OneTube reopens on the last one.
    private val hiddenTabsState = androidx.compose.runtime.mutableStateOf(store.getStringSet("hidden_tabs") ?: emptySet())
    var hiddenTabs: Set<String>
        get() = hiddenTabsState.value
        set(value) { hiddenTabsState.value = value; store.putStringSet("hidden_tabs", value) }

    private val tabOrderState = androidx.compose.runtime.mutableStateOf(strings("tab_order"))
    var tabOrder: List<String>
        get() = tabOrderState.value
        set(value) { tabOrderState.value = value; store.putString("tab_order", value.joinToString("\n")) }

    private val openOnLastTabState = bool("open_on_last_tab", false)
    var openOnLastTab: Boolean
        get() = openOnLastTabState.value
        set(value) { openOnLastTabState.value = value; store.putBoolean("open_on_last_tab", value) }

    var lastTab: String
        get() = store.getString("last_tab").orEmpty()
        set(value) = store.putString("last_tab", value)

    // SponsorBlock behaviour (on top of [sponsorSkip]).
    private val sponsorAskState = bool("sponsor_ask", false)
    /** Show a "Skip" card when a marked part starts instead of skipping it. */
    var sponsorAsk: Boolean
        get() = sponsorAskState.value
        set(value) { sponsorAskState.value = value; store.putBoolean("sponsor_ask", value) }

    private val sponsorNoticeState = bool("sponsor_notice", true)
    /** Show a short "Skipped sponsor" message after an automatic skip. */
    var sponsorNotice: Boolean
        get() = sponsorNoticeState.value
        set(value) { sponsorNoticeState.value = value; store.putBoolean("sponsor_notice", value) }

    fun reset() {
        store.removeAll(
            listOf(
                "autoplay",
                "sponsor_skip",
                "resume_playback",
                "preferred_quality",
                "preferred_audio_language",
                "storyboard_previews",
                "live_chat",
                "header_clock",
                "auto_subtitles",
                "subtitle_language",
                "posters_per_row",
                "hide_shorts",
                "hide_watched",
                "hide_upcoming",
                "hidden_words",
                "hidden_channels",
                "hidden_tabs",
                "tab_order",
                "open_on_last_tab",
                "sponsor_ask",
                "sponsor_notice",
                "translate_captions",
                "live_chat_font",
                "live_chat_opacity",
            ),
        )
        translateCaptionsState.value = ""
        liveChatFontState.value = 13
        liveChatOpacityState.value = 100
        headerClockState.value = false
        autoSubtitlesState.value = false
        postersPerRowState.value = 0
        hideShortsState.value = false
        hideWatchedState.value = false
        hideUpcomingState.value = false
        hiddenWordsState.value = emptyList()
        hiddenChannelsState.value = emptyList()
        hiddenTabsState.value = emptySet()
        tabOrderState.value = emptyList()
        openOnLastTabState.value = false
        sponsorAskState.value = false
        sponsorNoticeState.value = true
    }

    fun qualityHeight(label: String): Int? = when (label) {
        "720p" -> 720
        "1080p" -> 1080
        "1440p" -> 1440
        "4K" -> 2160
        else -> null
    }

    fun audioLanguageCode(label: String): String? = when (label) {
        "English" -> "en"
        "Spanish" -> "es"
        "French" -> "fr"
        "German" -> "de"
        "Japanese" -> "ja"
        "Korean" -> "ko"
        "Portuguese" -> "pt"
        "Hindi" -> "hi"
        else -> null
    }
}
