package com.nuvio.app.features.youtube

import com.nuvio.app.core.ui.SectionPalette

import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.Window
import nuvio.composeapp.generated.resources.nuvio_one_logo_red
import nuvio.composeapp.generated.resources.Res
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.InputChip
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.features.iptv.MultiviewTile
import com.nuvio.app.features.iptv.PlaybackTarget
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.isFullscreenActionActive
import com.nuvio.app.core.ui.toggleFullscreenAction
import com.nuvio.app.core.ui.NuvioDesktopAnimation
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem
import com.liskovsoft.mediaserviceinterfaces.data.NotificationState
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.BuiltInSubtitleFonts
import com.nuvio.app.features.player.PlayerControlChapter
import com.nuvio.app.features.player.PlayerControlInfoItem
import com.nuvio.app.features.player.PlayerControlInfoSection
import com.nuvio.app.features.player.PlayerControlSubtitleFont
import com.nuvio.app.features.player.PlayerControlSubtitleLanguageItem
import com.nuvio.app.features.player.PlayerControlSubtitleOptionItem
import com.nuvio.app.features.player.PlayerControlsAction
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.SubtitleFonts
import com.nuvio.app.features.player.applySubtitleStyleControlEvent
import com.nuvio.app.features.player.formatPlaybackSpeedLabel
import com.nuvio.app.features.player.rememberSubtitleFontImporter
import com.nuvio.app.features.player.desktop.NativeAudioTrack
import com.nuvio.app.features.player.desktop.NativePlayerController
import com.nuvio.app.features.player.desktop.DesktopPlayerPictureInPicture
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val YouTubeBackground: Color get() = SectionPalette.background
private val YouTubeHeader: Color get() = SectionPalette.background
private val YouTubeRed = Color(0xFFFF1F45)
private val YouTubePanel: Color get() = SectionPalette.surface
private val YouTubePanelRaised: Color get() = SectionPalette.raised
private val YouTubeBorder: Color get() = SectionPalette.border
private val YouTubeTextMuted: Color get() = SectionPalette.muted
private val YouTubeTextDim: Color get() = SectionPalette.muted

private enum class YouTubeTab(
    val label: String,
    val grid: Boolean = false,
    val needsSignIn: Boolean = false,
) {
    HOME("Home", needsSignIn = true),
    SEARCH("Search"),
    LIVE("Live"),
    NEWS("News"),
    GAMING("Gaming"),
    SPORTS("Sports"),
    SUBSCRIPTIONS("Subscriptions", grid = true, needsSignIn = true),
    HISTORY("History", grid = true, needsSignIn = true),
    NOTIFICATIONS("Notifications", grid = true, needsSignIn = true),
    PLAYLISTS("Playlists", grid = true, needsSignIn = true),
    CHANNELS("Channels", grid = true, needsSignIn = true),
    SHORTS("Shorts", grid = true),
    MUSIC("Music"),
}

private sealed interface YouTubeOverlay {
    val title: String

    data class Channel(
        override val title: String,
        val channelId: String,
    ) : YouTubeOverlay

    data class Playlist(
        override val title: String,
        val playlistId: String,
        val reloadPageKey: String?,
    ) : YouTubeOverlay
}

/** A real Short: the Shorts list also holds some full-length videos, which are longer than a Short can be. */
private fun YouTubeVideoItem.isShortClip(): Boolean =
    isShorts && (durationMs <= 0L || durationMs <= 180_000L)

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}

private fun videoMeta(item: YouTubeVideoItem): String {
    return item.metadata?.takeIf { it.isNotBlank() } ?: item.author.orEmpty()
}

private fun selectPreferredAudioTrack(
    tracks: List<YouTubeAudioTrack>,
    preferredLanguage: String,
): YouTubeAudioTrack? {
    if (tracks.isEmpty()) return null
    if (preferredLanguage.equals("Original", ignoreCase = true)) {
        return tracks.firstOrNull { it.isOriginal } ?: tracks.first()
    }
    val code = YouTubeSettingsStore.audioLanguageCode(preferredLanguage)
    return tracks.firstOrNull { track ->
        (!code.isNullOrBlank() && track.language?.equals(code, ignoreCase = true) == true) ||
            track.label.contains(preferredLanguage, ignoreCase = true)
    } ?: tracks.firstOrNull { it.isOriginal } ?: tracks.first()
}

@Composable
internal fun YouTubeDesktopScreen(onBack: () -> Unit) {
    YouTubeApp(onBack = onBack, music = false)
}

@Composable
private fun YouTubeApp(
    onBack: () -> Unit,
    music: Boolean,
    onPlayMusic: ((YouTubeVideoItem) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val hiddenTabs = YouTubeSettingsStore.hiddenTabs
    val tabOrder = YouTubeSettingsStore.tabOrder
    val tabs = remember(music, hiddenTabs, tabOrder) {
        if (music) {
            listOf(YouTubeTab.MUSIC)
        } else {
            orderedOneTubeTabs(tabOrder).filter { it.name !in hiddenTabs }
                .ifEmpty { listOf(YouTubeTab.HOME) }
        }
    }

    var tab by remember {
        val last = YouTubeSettingsStore.lastTab
        val reopen = if (!music && YouTubeSettingsStore.openOnLastTab) tabs.firstOrNull { it.name == last } else null
        mutableStateOf(reopen ?: tabs.first())
    }
    LaunchedEffect(tab) { if (!music) YouTubeSettingsStore.lastTab = tab.name }
    LaunchedEffect(tabs) { if (tab !in tabs && tab != YouTubeTab.SEARCH && tab != YouTubeTab.NOTIFICATIONS) tab = tabs.first() }
    var headerSearch by remember { mutableStateOf("") }
    var searchSeed by remember { mutableStateOf("") }
    var searchRequest by remember { mutableStateOf(0) }
    var castMenuOpen by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf<List<YouTubeAccountItem>>(emptyList()) }
    var selectedAccount by remember { mutableStateOf<YouTubeAccountItem?>(null) }
    var accountsOpen by remember { mutableStateOf(false) }
    var accountRevision by remember { mutableStateOf(0) }
    var notificationRevision by remember { mutableStateOf(0) }
    // Bumped after playlist edits so the Playlists tab reloads.
    var libraryRevision by remember { mutableStateOf(0) }
    var channelOrder by remember { mutableStateOf(YouTubeChannelOrder.DEFAULT) }
    // Local view of the account's watch-history recording (YouTube has no read call for it).
    var historyPaused by remember { mutableStateOf(false) }
    var libraryMessage by remember { mutableStateOf<String?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf<String?>(null) }
    var confirmDeletePlaylist by remember { mutableStateOf(false) }
    var signInCode by remember { mutableStateOf<String?>(null) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var signInDisposable by remember { mutableStateOf<Disposable?>(null) }

    var feeds by remember { mutableStateOf<List<YouTubeFeed>>(emptyList()) }
    var feedLoading by remember { mutableStateOf(false) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var loadingMoreFeed by remember { mutableStateOf(false) }
    var hasMoreFeed by remember { mutableStateOf(true) }

    var selected by remember { mutableStateOf<YouTubeVideoItem?>(null) }
    var details by remember { mutableStateOf<YouTubeDetails?>(null) }
    var playback by remember { mutableStateOf<YouTubePlayback?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<YouTubeComment>>(emptyList()) }
    var commentsNewest by remember { mutableStateOf(false) }
    var nextCommentsKey by remember { mutableStateOf<String?>(null) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentsError by remember { mutableStateOf<String?>(null) }
    var chatMessages by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var chatDisposable by remember { mutableStateOf<Disposable?>(null) }

    var overlay by remember { mutableStateOf<YouTubeOverlay?>(null) }
    var channelFeeds by remember { mutableStateOf<List<YouTubeFeed>>(emptyList()) }
    var channelLoading by remember { mutableStateOf(false) }
    var channelError by remember { mutableStateOf<String?>(null) }
    var channelSubscribed by remember { mutableStateOf(false) }
    var channelSubscriberCount by remember { mutableStateOf<String?>(null) }
    var playlistFeed by remember { mutableStateOf<YouTubeFeed?>(null) }
    var playlistLoading by remember { mutableStateOf(false) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var autoplay by remember { mutableStateOf(YouTubeSettingsStore.autoplay) }
    var skipSponsors by remember { mutableStateOf(YouTubeSettingsStore.sponsorSkip) }
    var resumePlayback by remember { mutableStateOf(YouTubeSettingsStore.resumePlayback) }
    var preferredQuality by remember { mutableStateOf(YouTubeSettingsStore.preferredQuality) }
    var preferredAudioLanguage by remember { mutableStateOf(YouTubeSettingsStore.preferredAudioLanguage) }
    var storyboardPreviews by remember { mutableStateOf(YouTubeSettingsStore.storyboardPreviews) }
    var liveChatEnabled by remember { mutableStateOf(YouTubeSettingsStore.liveChat) }
    var settingsOpen by remember { mutableStateOf(false) }
    var multiviewOpen by remember { mutableStateOf(false) }
    // Bumped by the refresh button to load the current tab's feed again.
    var feedRevision by remember { mutableStateOf(0) }
    var videoQueue by remember { mutableStateOf<List<YouTubeVideoItem>>(emptyList()) }
    var videoQueueIndex by remember { mutableStateOf(-1) }

    suspend fun loadAccounts() {
        runCatching { YouTubeEngine.refreshAccounts() }
        val items = runCatching { YouTubeEngine.accounts() }.getOrDefault(emptyList())
        accounts = items
        val current = items.firstOrNull { it.selected } ?: items.firstOrNull()
        selectedAccount = current
        accountName = current?.name
        if (items.isNotEmpty()) signedIn = true
    }

    fun switchAccount(account: YouTubeAccountItem) {
        if (account.selected) {
            accountsOpen = false
            return
        }
        accountsOpen = false
        scope.launch {
            actionMessage = null
            runCatching { YouTubeEngine.selectAccount(account.key) }
                .onSuccess {
                    selected = null
                    overlay = null
                    chatDisposable?.dispose()
                    chatDisposable = null
                    feeds = emptyList()
                    feedError = null
                    accountRevision++
                    loadAccounts()
                }
                .onFailure { actionMessage = it.message ?: "Could not switch YouTube profile." }
        }
    }

    LaunchedEffect(Unit) {
        signedIn = runCatching { YouTubeEngine.isSignedIn() }.getOrDefault(false)
        if (signedIn) {
            loadAccounts()
        } else {
            // Signed out here: drop any account the engine still holds, so suggestions and subscriptions aren't
            // taken from a previous sign-in.
            runCatching { YouTubeEngine.signOutAll() }
        }
    }

    LaunchedEffect(Unit) {
        // Let the first feed request start, then prepare the player signature
        // extractor so the first video doesn't wait for the ~7s JS solve.
        delay(1_500L)
        runCatching { YouTubeEngine.warmPlaybackData() }
    }

    DisposableEffect(Unit) {
        onDispose {
            signInDisposable?.dispose()
            chatDisposable?.dispose()
        }
    }

    fun startSignIn() {
        if (signInDisposable != null) return
        signInError = null
        scope.launch {
            runCatching {
                YouTubeEngine.startSignIn(
                    onCode = { code ->
                        signInCode = code
                        // Open Google's device page with the code already in the link; the user only signs in and approves.
                        uriHandler.openUri("https://www.google.com/device?user_code=${code.filterNot { it.isWhitespace() }}")
                    },
                    onComplete = {
                        signInCode = null
                        signInDisposable = null
                        signedIn = true
                        scope.launch {
                            loadAccounts()
                            // More than one profile on the account: let the user pick which one to use right after sign-in.
                            if (accounts.size > 1) accountsOpen = true
                        }
                    },
                    onError = {
                        signInCode = null
                        signInDisposable = null
                        signInError = it.message ?: "Sign-in failed."
                    },
                )
            }.onSuccess {
                signInDisposable = it
            }.onFailure {
                signInError = it.message ?: "Sign-in failed."
            }
        }
    }

    fun loadComments(key: String?, append: Boolean) {
        if (key.isNullOrBlank() || commentsLoading) return
        scope.launch {
            commentsLoading = true
            commentsError = null
            runCatching { YouTubeEngine.comments(key) }
                .onSuccess { page ->
                    comments = if (append) comments + page.comments else page.comments
                    nextCommentsKey = page.nextCommentsKey
                }
                .onFailure { commentsError = it.message ?: "Could not load comments." }
            commentsLoading = false
        }
    }

    fun selectCommentSort(newest: Boolean) {
        if (commentsNewest == newest || commentsLoading) return
        commentsNewest = newest
        comments = emptyList()
        nextCommentsKey = null
        loadComments(if (newest) details?.newestCommentsKey else details?.commentsKey, append = false)
    }

    fun openVideo(item: YouTubeVideoItem, queueItems: List<YouTubeVideoItem>? = null) {
        if (queueItems != null) {
            videoQueue = queueItems
            videoQueueIndex = queueItems.indexOfFirst { it.videoId == item.videoId }
                .takeIf { it >= 0 } ?: 0
        } else {
            val queuePosition = videoQueue.indexOfFirst { it.videoId == item.videoId }
            if (queuePosition >= 0) {
                videoQueueIndex = queuePosition
            } else {
                videoQueue = emptyList()
                videoQueueIndex = -1
            }
        }
        selected = item
        details = null
        playback = null
        playbackError = null
        detailError = null
        comments = emptyList()
        commentsNewest = false
        nextCommentsKey = null
        commentsError = null
        chatMessages = emptyList()
        chatDisposable?.dispose()
        chatDisposable = null
        actionMessage = null
        resolving = true

        scope.launch {
            val resolveStart = System.currentTimeMillis()
            OneTubeDebugLog.line("resolve start video=${item.videoId}")
            runCatching {
                YouTubeEngine.resolvePlayback(
                    item,
                    music,
                    YouTubeSettingsStore.qualityHeight(preferredQuality),
                )
            }
                .onSuccess { playback = it }
                .onFailure { playbackError = it.message ?: "Playback could not be prepared." }
            OneTubeDebugLog.line("resolve done in ${System.currentTimeMillis() - resolveStart} ms ok=${playback != null}")
            resolving = false

            val loadedDetails = runCatching { YouTubeEngine.details(item.videoId) }
                .onFailure { detailError = it.message ?: "Could not load video details." }
                .getOrNull()
            details = loadedDetails

            loadedDetails?.commentsKey?.let { loadComments(it, append = false) }

            if (liveChatEnabled) {
                loadedDetails?.liveChatKey?.takeIf { it.isNotBlank() }?.let { chatKey ->
                    chatDisposable = YouTubeEngine.liveChat(chatKey).subscribe(
                        { message -> chatMessages = (chatMessages + message).takeLast(100) },
                        { },
                    )
                }
            }
        }
    }

    fun openItem(item: YouTubeVideoItem) {
        when (item.kind) {
            YouTubeItemKind.VIDEO -> if (music && onPlayMusic != null) {
                onPlayMusic(item)
            } else {
                // A Short queues every Short on screen, so Back and Forward move through them.
                openVideo(item, if (item.isShortClip()) feeds.flatMap { it.items }.filter { it.isShortClip() }.distinctBy { it.videoId } else null)
            }
            YouTubeItemKind.CHANNEL -> {
                overlay = if (item.channelId == null && item.reloadPageKey != null) {
                    // A subscribed-channel card from the Channels grid: it opens by its browse key, not a channel id.
                    YouTubeOverlay.Playlist(
                        title = item.title,
                        playlistId = item.videoId,
                        reloadPageKey = item.reloadPageKey,
                    )
                } else {
                    YouTubeOverlay.Channel(
                        title = item.title,
                        channelId = item.channelId ?: item.videoId,
                    )
                }
            }

            YouTubeItemKind.PLAYLIST -> {
                overlay = YouTubeOverlay.Playlist(
                    title = item.title,
                    playlistId = item.playlistId ?: item.videoId,
                    reloadPageKey = item.reloadPageKey,
                )
            }
        }
    }

    fun openChannel(channelId: String, name: String) {
        selected = null
        chatDisposable?.dispose()
        chatDisposable = null
        actionMessage = null
        overlay = YouTubeOverlay.Channel(channelId = channelId, title = name)
    }

    fun toggleLike() {
        val current = details ?: return
        if (!signedIn) {
            actionMessage = "Sign in to like videos."
            return
        }
        scope.launch {
            actionMessage = null
            val newLike = current.likeStatus != 1
            runCatching { YouTubeEngine.setLike(current.videoId, current.channelId, newLike) }
                .onSuccess {
                    details = runCatching { YouTubeEngine.details(current.videoId) }.getOrNull() ?: details
                }
                .onFailure { actionMessage = it.message ?: "Could not update like." }
        }
    }

    fun toggleDislike() {
        val current = details ?: return
        if (!signedIn) {
            actionMessage = "Sign in to dislike videos."
            return
        }
        scope.launch {
            actionMessage = null
            val newDislike = current.likeStatus != 2
            runCatching { YouTubeEngine.setDislike(current.videoId, current.channelId, newDislike) }
                .onSuccess {
                    details = runCatching { YouTubeEngine.details(current.videoId) }.getOrNull() ?: details
                }
                .onFailure { actionMessage = it.message ?: "Could not update dislike." }
        }
    }

    fun toggleSubscribe() {
        val current = details ?: return
        val channelId = current.channelId ?: return
        if (!signedIn) {
            actionMessage = "Sign in to subscribe."
            return
        }
        scope.launch {
            actionMessage = null
            val newSubscribed = !current.isSubscribed
            runCatching { YouTubeEngine.setSubscribed(channelId, newSubscribed) }
                .onSuccess {
                    details = details?.copy(isSubscribed = newSubscribed)
                    actionMessage = if (newSubscribed) "Subscribed" else "Unsubscribed"
                }
                .onFailure { actionMessage = it.message ?: "Could not update subscription." }
        }
    }

    fun setNotificationState(state: NotificationState) {
        val videoId = selected?.videoId ?: return
        if (!signedIn) {
            actionMessage = "Sign in to change notifications."
            return
        }
        scope.launch {
            actionMessage = null
            runCatching { YouTubeEngine.setNotificationState(state) }
                .onSuccess {
                    details = runCatching { YouTubeEngine.details(videoId) }.getOrNull()
                    actionMessage = "Notification setting updated"
                }
                .onFailure { actionMessage = it.message ?: "Could not update notifications." }
        }
    }

    fun toggleChannelSubscribe() {
        val current = overlay as? YouTubeOverlay.Channel ?: return
        if (!signedIn) {
            actionMessage = "Sign in to subscribe."
            return
        }
        scope.launch {
            actionMessage = null
            val newSubscribed = !channelSubscribed
            runCatching { YouTubeEngine.setSubscribed(current.channelId, newSubscribed) }
                .onSuccess {
                    channelSubscribed = newSubscribed
                    actionMessage = if (newSubscribed) "Subscribed" else "Unsubscribed"
                }
                .onFailure { actionMessage = it.message ?: "Could not update subscription." }
        }
    }

    fun hideNotification(item: YouTubeVideoItem) {
        scope.launch {
            actionMessage = null
            runCatching { YouTubeEngine.hideNotification(item) }
                .onSuccess { notificationRevision++ }
                .onFailure { actionMessage = it.message ?: "Could not hide this notification." }
        }
    }

    fun toggleCommentLike(comment: YouTubeComment) {
        val actionKey = comment.likeActionKey ?: return
        if (!signedIn) {
            actionMessage = "Sign in to like comments."
            return
        }
        scope.launch {
            actionMessage = null
            runCatching { YouTubeEngine.toggleCommentLike(actionKey) }
                .onSuccess {
                    val commentsKey = details?.commentsKey
                    if (commentsKey != null) {
                        runCatching { YouTubeEngine.comments(commentsKey) }
                            .getOrNull()
                            ?.let { page ->
                                comments = page.comments
                                nextCommentsKey = page.nextCommentsKey
                            }
                    } else {
                        comments = comments.map {
                            if (it.likeActionKey == actionKey) it.copy(liked = !it.liked) else it
                        }
                    }
                }
                .onFailure { actionMessage = it.message ?: "Could not like comment." }
        }
    }

    fun toggleCommentDislike(comment: YouTubeComment) {
        val actionKey = comment.dislikeActionKey ?: return
        if (!signedIn) {
            actionMessage = "Sign in to dislike comments."
            return
        }
        scope.launch {
            actionMessage = null
            runCatching { YouTubeEngine.toggleCommentDislike(actionKey) }
                .onSuccess {
                    val commentsKey = if (commentsNewest) details?.newestCommentsKey else details?.commentsKey
                    if (commentsKey != null) {
                        runCatching { YouTubeEngine.comments(commentsKey) }
                            .getOrNull()
                            ?.let { page ->
                                comments = page.comments
                                nextCommentsKey = page.nextCommentsKey
                            }
                    }
                }
                .onFailure { actionMessage = it.message ?: "Could not dislike comment." }
        }
    }

    LaunchedEffect(overlay) {
        when (val current = overlay) {
            is YouTubeOverlay.Channel -> {
                channelLoading = true
                channelError = null
                channelFeeds = emptyList()
                channelSubscribed = false
                channelSubscriberCount = null
                runCatching { YouTubeEngine.channel(current.channelId) }
                    .onSuccess { feeds ->
                        channelFeeds = feeds
                        val firstVideo = feeds.asSequence()
                            .flatMap { it.items.asSequence() }
                            .firstOrNull { it.kind == YouTubeItemKind.VIDEO }
                        if (firstVideo != null) {
                            runCatching { YouTubeEngine.details(firstVideo.videoId) }
                                .getOrNull()
                                ?.let { meta ->
                                    channelSubscribed = meta.isSubscribed
                                    channelSubscriberCount = meta.subscriberCount
                                }
                        }
                    }
                    .onFailure { channelError = it.message ?: "Could not load this channel." }
                channelLoading = false
            }

            is YouTubeOverlay.Playlist -> {
                playlistLoading = true
                playlistError = null
                playlistFeed = null
                val primary = runCatching { YouTubeEngine.playlist(current.playlistId) }.getOrNull()
                playlistFeed = if (primary != null && primary.items.isNotEmpty()) {
                    primary
                } else {
                    current.reloadPageKey?.let { key ->
                        runCatching { YouTubeEngine.group(key) }.getOrNull()
                    } ?: primary
                }
                if (playlistFeed == null) playlistError = "Could not load this playlist."
                playlistLoading = false
            }

            null -> {
                channelFeeds = emptyList()
                channelError = null
                playlistFeed = null
                playlistError = null
            }
        }
    }

    LaunchedEffect(feeds) {
        DeArrowDesktop.requestItems(feeds.flatMap { it.items })
    }

    fun feedKeyForTab(): String? = when (tab) {
        YouTubeTab.HOME -> "home"
        YouTubeTab.LIVE -> "live"
        YouTubeTab.NEWS -> "news"
        YouTubeTab.GAMING -> "gaming"
        YouTubeTab.SPORTS -> "sports"
        YouTubeTab.MUSIC -> "music"
        YouTubeTab.SHORTS -> "shorts"
        YouTubeTab.SUBSCRIPTIONS -> "subscriptions"
        YouTubeTab.HISTORY -> "history"
        YouTubeTab.PLAYLISTS -> "playlists"
        YouTubeTab.CHANNELS -> "channels"
        else -> null
    }

    fun loadMoreFeed() {
        val key = feedKeyForTab() ?: return
        if (feedLoading || loadingMoreFeed || !hasMoreFeed) return
        val tabAtStart = tab
        loadingMoreFeed = true
        scope.launch {
            val additions = runCatching { YouTubeEngine.continueFeed(key) }.getOrDefault(emptyList())
            if (tab == tabAtStart) {
                if (additions.isEmpty()) {
                    hasMoreFeed = false
                } else {
                    feeds = feeds + additions
                }
            }
            loadingMoreFeed = false
        }
    }

    LaunchedEffect(tab, signedIn, accountRevision, notificationRevision, libraryRevision, channelOrder, feedRevision) {
        hasMoreFeed = tab != YouTubeTab.SEARCH && tab != YouTubeTab.NOTIFICATIONS
        loadingMoreFeed = false
        if (tab == YouTubeTab.SEARCH) return@LaunchedEffect
        if (tab.needsSignIn && !signedIn) {
            feeds = emptyList()
            feedError = null
            feedLoading = false
            hasMoreFeed = false
            return@LaunchedEffect
        }

        feedLoading = true
        feedError = null
        runCatching {
            when (tab) {
                YouTubeTab.HOME -> YouTubeEngine.home()
                YouTubeTab.LIVE -> YouTubeEngine.live()
                YouTubeTab.NEWS -> YouTubeEngine.news()
                YouTubeTab.GAMING -> YouTubeEngine.gaming()
                YouTubeTab.SPORTS -> YouTubeEngine.sports()
                YouTubeTab.SUBSCRIPTIONS -> YouTubeEngine.subscriptions()
                YouTubeTab.HISTORY -> YouTubeEngine.history()
                YouTubeTab.NOTIFICATIONS -> YouTubeEngine.notifications()
                YouTubeTab.PLAYLISTS -> YouTubeEngine.playlists()
                YouTubeTab.CHANNELS -> YouTubeEngine.subscribedChannels(channelOrder)
                YouTubeTab.SHORTS -> YouTubeEngine.shorts()
                YouTubeTab.MUSIC -> YouTubeEngine.music()
                YouTubeTab.SEARCH -> emptyList()
            }
        }
            .onSuccess {
                if (tab == YouTubeTab.CHANNELS) {
                    OneTubeDebugLog.line("channels tab: ${it.sumOf { feed -> feed.items.size }} channels in ${it.size} feeds, order=$channelOrder")
                }
                feeds = it
            }
            .onFailure {
                if (tab == YouTubeTab.CHANNELS) OneTubeDebugLog.line("channels tab failed: ${it.javaClass.simpleName}: ${it.message}")
                feedError = it.message ?: "Could not load this feed."
            }
        feedLoading = false
    }

    com.nuvio.app.core.ui.CoverNativeVideo(settingsOpen || castMenuOpen || accountsOpen || signInCode != null)
    Surface(color = YouTubeBackground, contentColor = Color.White) {
        Column(Modifier.fillMaxSize()) {
            // Window full screen with a video open: the player covers everything, so no header.
            if (!(isFullscreenActionActive() && selected != null)) {
            Column(Modifier.fillMaxWidth().background(YouTubeHeader)) {
                Box(Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 22.dp)) {
                    Row(
                        Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Nuvio",
                                tint = YouTubeTextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            androidx.compose.foundation.Image(
                                painter = org.jetbrains.compose.resources.painterResource(Res.drawable.nuvio_one_logo_red),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                            Column {
                                Text(
                                    if (music) "YouTube Music" else "OneTube",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp,
                                )
                                Text(
                                    "NUVIO DESKTOP",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        letterSpacing = 1.4.sp,
                                    ),
                                    color = YouTubeTextDim,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = headerSearch,
                        onValueChange = { headerSearch = it },
                        modifier = Modifier.align(Alignment.Center)
                            .fillMaxWidth(0.42f)
                            .widthIn(max = 560.dp),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "Search videos, channels, playlists",
                                color = YouTubeTextDim,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = YouTubeTextDim,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        shape = RoundedCornerShape(50),
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                val query = headerSearch.trim()
                                if (query.isNotEmpty()) {
                                    searchSeed = query
                                    searchRequest += 1
                                    tab = YouTubeTab.SEARCH
                                    selected = null
                                    overlay = null
                                    actionMessage = null
                                }
                            },
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = YouTubePanel,
                            unfocusedContainerColor = YouTubePanel,
                            focusedBorderColor = YouTubeBorder,
                            unfocusedBorderColor = YouTubeBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = YouTubeRed,
                        ),
                    )

                    Row(
                        Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box {
                            IconButton(onClick = { castMenuOpen = !castMenuOpen }) {
                                Icon(
                                    Icons.Filled.Cast,
                                    contentDescription = "Cast",
                                    tint = YouTubeTextMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = castMenuOpen,
                                onDismissRequest = { castMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("No cast devices found") },
                                    onClick = { castMenuOpen = false },
                                )
                            }
                        }
                        IconButton(onClick = {
                            tab = YouTubeTab.NOTIFICATIONS
                            selected = null
                            overlay = null
                            actionMessage = null
                        }) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = "Notifications",
                                tint = YouTubeTextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (YouTubeSettingsStore.headerClock) OneTubeHeaderClock()
                        if (tab == YouTubeTab.SHORTS) {
                            IconButton(onClick = { feedRevision++ }) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refresh Shorts",
                                    tint = YouTubeTextMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        IconButton(onClick = { multiviewOpen = true }) {
                            Icon(
                                Icons.Filled.GridView,
                                contentDescription = "Multiview",
                                tint = YouTubeTextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "OneTube settings",
                                tint = YouTubeTextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (signedIn) {
                            Box {
                                TextButton(onClick = { accountsOpen = !accountsOpen }) {
                                    YouTubeAccountAvatar(selectedAccount)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        selectedAccount?.name ?: accountName ?: "Signed in",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray,
                                    )
                                    Icon(
                                        Icons.Filled.ExpandMore,
                                        contentDescription = "Switch YouTube profile",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = accountsOpen,
                                    onDismissRequest = { accountsOpen = false },
                                ) {
                                    if (accounts.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No YouTube profiles found") },
                                            onClick = { accountsOpen = false },
                                        )
                                    } else {
                                        accounts.forEach { account ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(account.name)
                                                        account.email
                                                            ?.takeIf { it.isNotBlank() && it != account.name }
                                                            ?.let {
                                                                Text(
                                                                    it,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = Color.Gray,
                                                                )
                                                            }
                                                    }
                                                },
                                                leadingIcon = { YouTubeAccountAvatar(account) },
                                                trailingIcon = if (account.selected) {
                                                    {
                                                        Icon(
                                                            Icons.Filled.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                } else {
                                                    null
                                                },
                                                onClick = { switchAccount(account) },
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = YouTubeBorder)
                                    DropdownMenuItem(
                                        text = { Text("Use another account") },
                                        onClick = {
                                            accountsOpen = false
                                            startSignIn()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sign out") },
                                        onClick = {
                                            accountsOpen = false
                                            scope.launch {
                                                runCatching { YouTubeEngine.signOutAll() }
                                                accounts = emptyList()
                                                selectedAccount = null
                                                accountName = null
                                                signedIn = false
                                                selected = null
                                                overlay = null
                                                feeds = emptyList()
                                                accountRevision++
                                            }
                                        },
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = ::startSignIn,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = YouTubeRed,
                                    contentColor = Color.White,
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp,
                                ),
                            ) { Text("Sign in") }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { entry ->
                        val selectedTab = tab == entry
                        Text(
                            entry.label,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selectedTab) YouTubeRed else YouTubePanel)
                                .clickable {
                                    tab = entry
                                    selected = null
                                    overlay = null
                                    actionMessage = null
                                }
                                .padding(horizontal = 15.dp, vertical = 8.dp),
                            color = if (selectedTab) Color.White else YouTubeTextMuted,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
            HorizontalDivider(color = YouTubeBorder)
            }

            if (settingsOpen) {
                YouTubeSettingsDialog(
                    autoplay = autoplay,
                    onAutoplayChange = {
                        autoplay = it
                        YouTubeSettingsStore.autoplay = it
                    },
                    skipSponsors = skipSponsors,
                    onSkipSponsorsChange = {
                        skipSponsors = it
                        YouTubeSettingsStore.sponsorSkip = it
                    },
                    dearrow = DeArrowDesktop.enabled,
                    onDeArrowChange = { DeArrowDesktop.enabled = it },
                    resumePlayback = resumePlayback,
                    onResumePlaybackChange = {
                        resumePlayback = it
                        YouTubeSettingsStore.resumePlayback = it
                    },
                    preferredQuality = preferredQuality,
                    onPreferredQualityChange = {
                        preferredQuality = it
                        YouTubeSettingsStore.preferredQuality = it
                    },
                    audioLanguage = preferredAudioLanguage,
                    onAudioLanguageChange = {
                        preferredAudioLanguage = it
                        YouTubeSettingsStore.preferredAudioLanguage = it
                    },
                    storyboardPreviews = storyboardPreviews,
                    onStoryboardPreviewsChange = {
                        storyboardPreviews = it
                        YouTubeSettingsStore.storyboardPreviews = it
                    },
                    liveChat = liveChatEnabled,
                    onLiveChatChange = {
                        liveChatEnabled = it
                        YouTubeSettingsStore.liveChat = it
                    },
                    signedIn = signedIn,
                    accountName = selectedAccount?.name ?: accountName,
                    onSwitchAccount = {
                        settingsOpen = false
                        accountsOpen = true
                    },
                    onSignIn = {
                        settingsOpen = false
                        startSignIn()
                    },
                    onReset = {
                        YouTubeSettingsStore.reset()
                        DeArrowDesktop.enabled = true
                        autoplay = YouTubeSettingsStore.autoplay
                        skipSponsors = YouTubeSettingsStore.sponsorSkip
                        resumePlayback = YouTubeSettingsStore.resumePlayback
                        preferredQuality = YouTubeSettingsStore.preferredQuality
                        preferredAudioLanguage = YouTubeSettingsStore.preferredAudioLanguage
                        storyboardPreviews = YouTubeSettingsStore.storyboardPreviews
                        liveChatEnabled = YouTubeSettingsStore.liveChat
                    },
                    onDismiss = { settingsOpen = false },
                )
            }

            Box(Modifier.weight(1f)) {
            when {
                selected != null -> VideoDetailPane(
                    modifier = Modifier.fillMaxSize(),
                    music = music,
                    resumePlayback = resumePlayback,
                    preferredQuality = preferredQuality,
                    preferredAudioLanguage = preferredAudioLanguage,
                    storyboardPreviews = storyboardPreviews,
                    liveChatEnabled = liveChatEnabled,
                    selected = selected!!,
                    details = details,
                    playback = playback,
                    playbackError = playbackError,
                    detailError = detailError,
                    resolving = resolving,
                    comments = comments,
                    commentsNewest = commentsNewest,
                    canSortComments = details?.newestCommentsKey != null,
                    onCommentSortChange = ::selectCommentSort,
                    queueIndex = videoQueueIndex,
                    queueCount = videoQueue.size,
                    onQueueJump = { index ->
                        val target = videoQueue.getOrNull(index)
                        if (target != null && index != videoQueueIndex) {
                            videoQueueIndex = index
                            openVideo(target, videoQueue)
                        }
                    },
                    queuePrevious = videoQueue.getOrNull(videoQueueIndex - 1),
                    onQueuePrevious = {
                        val previousIndex = videoQueueIndex - 1
                        val previous = videoQueue.getOrNull(previousIndex)
                        if (previous != null) {
                            videoQueueIndex = previousIndex
                            openVideo(previous, videoQueue)
                        }
                    },
                    queueNext = videoQueue.getOrNull(videoQueueIndex + 1),
                    onQueueNext = {
                        val nextIndex = videoQueueIndex + 1
                        val next = videoQueue.getOrNull(nextIndex)
                        if (next != null) {
                            videoQueueIndex = nextIndex
                            openVideo(next, videoQueue)
                        }
                    },
                    nextCommentsKey = nextCommentsKey,
                    commentsLoading = commentsLoading,
                    commentsError = commentsError,
                    chatMessages = chatMessages,
                    signedIn = signedIn,
                    actionMessage = actionMessage,
                    onBack = {
                        selected = null
                        chatDisposable?.dispose()
                        chatDisposable = null
                        actionMessage = null
                    },
                    onLoadMoreComments = {
                        loadComments(
                            nextCommentsKey
                                ?: if (commentsNewest) details?.newestCommentsKey else details?.commentsKey,
                            append = true,
                        )
                    },
                    onPlaybackError = { playbackError = it },
                    onOpen = ::openItem,
                    onLike = ::toggleLike,
                    onDislike = ::toggleDislike,
                    onSubscribe = ::toggleSubscribe,
                    onNotificationState = ::setNotificationState,
                    onOpenChannel = ::openChannel,
                    autoplay = autoplay,
                    onAutoplayChange = {
                        autoplay = it
                        YouTubeSettingsStore.autoplay = it
                    },
                    skipSponsors = skipSponsors,
                    onSkipSponsorsChange = {
                        skipSponsors = it
                        YouTubeSettingsStore.sponsorSkip = it
                    },
                    onToggleCommentLike = ::toggleCommentLike,
                    onToggleCommentDislike = ::toggleCommentDislike,
                )

                overlay != null -> when (val current = overlay!!) {
                    is YouTubeOverlay.Channel -> ChannelPane(
                        modifier = Modifier.fillMaxSize(),
                        overlay = current,
                        feeds = channelFeeds,
                        loading = channelLoading,
                        error = channelError,
                        subscribed = channelSubscribed,
                        subscriberCount = channelSubscriberCount,
                        signedIn = signedIn,
                        actionMessage = actionMessage,
                        onBack = {
                            overlay = null
                            actionMessage = null
                        },
                        onToggleSubscribe = ::toggleChannelSubscribe,
                        onOpen = ::openItem,
                    )

                    is YouTubeOverlay.Playlist -> PlaylistPane(
                        modifier = Modifier.fillMaxSize(),
                        overlay = current,
                        feed = playlistFeed,
                        loading = playlistLoading,
                        error = playlistError,
                        onBack = { overlay = null },
                        onOpen = { item, queue ->
                            if (item.kind == YouTubeItemKind.VIDEO) openVideo(item, queue) else openItem(item)
                        },
                        onPlayQueue = { queue -> queue.firstOrNull()?.let { openVideo(it, queue) } },
                        // Only the account's own playlists (PL…) can be edited; Watch later
                        // and Liked videos (WL / LL) are YouTube's built-in lists.
                        canEdit = current.playlistId.startsWith("PL"),
                        onRename = { renameDraft = current.title },
                        onDelete = { confirmDeletePlaylist = true },
                        onRemoveItem = { item ->
                            val playlistId = current.playlistId
                            scope.launch {
                                runCatching { YouTubeEngine.removeFromPlaylist(playlistId, item.videoId) }
                                    .onSuccess {
                                        playlistFeed = playlistFeed?.let { feed ->
                                            feed.copy(items = feed.items.filterNot { it.videoId == item.videoId })
                                        }
                                    }
                                    .onFailure { libraryMessage = it.message ?: "Could not remove this video." }
                            }
                        },
                    )
                }

                else -> {
                    signInError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                    }

                    val shownFeeds = remember(
                        feeds,
                        tab,
                        YouTubeSettingsStore.hideShorts,
                        YouTubeSettingsStore.hideWatched,
                        YouTubeSettingsStore.hideUpcoming,
                        YouTubeSettingsStore.hiddenWords,
                        YouTubeSettingsStore.hiddenChannels,
                    ) {
                        if (tab == YouTubeTab.HISTORY || tab == YouTubeTab.SHORTS) {
                            feeds
                        } else {
                            feeds.map { feed -> feed.copy(items = feed.items.filterNot(YouTubeSettingsStore::hides)) }
                        }
                    }
                    when {
                        multiviewOpen -> YouTubeMultiview(
                            candidates = shownFeeds.flatMap { it.items }
                                .filter { it.kind == YouTubeItemKind.VIDEO }
                                .distinctBy { it.videoId },
                            onClose = { multiviewOpen = false },
                            modifier = Modifier.fillMaxSize(),
                        )

                        tab == YouTubeTab.SEARCH -> SearchPane(
                            modifier = Modifier.fillMaxSize(),
                            onOpen = ::openItem,
                            initialQuery = searchSeed,
                            searchRequest = searchRequest,
                        )

                        tab.needsSignIn && !signedIn ->
                            SignInPrompt(onSignIn = ::startSignIn)

                        tab == YouTubeTab.NOTIFICATIONS -> NotificationPane(
                            modifier = Modifier.fillMaxSize(),
                            feeds = shownFeeds,
                            loading = feedLoading,
                            error = feedError,
                            onOpen = ::openItem,
                            onHide = ::hideNotification,
                        )

                        tab == YouTubeTab.HOME -> HomeFeedPane(
                            modifier = Modifier.fillMaxSize(),
                            feeds = shownFeeds,
                            loading = feedLoading,
                            loadingMore = loadingMoreFeed,
                            hasMore = hasMoreFeed,
                            error = feedError,
                            onLoadMore = { loadMoreFeed() },
                            onOpen = ::openItem,
                        )

                        tab == YouTubeTab.SHORTS -> ShortsFeedPane(
                            modifier = Modifier.fillMaxSize(),
                            feeds = shownFeeds,
                            loading = feedLoading,
                            loadingMore = loadingMoreFeed,
                            hasMore = hasMoreFeed,
                            error = feedError,
                            onLoadMore = { loadMoreFeed() },
                            onOpen = ::openItem,
                        )

                        tab == YouTubeTab.MUSIC -> MusicFeedPane(
                            modifier = Modifier.fillMaxSize(),
                            feeds = shownFeeds,
                            loading = feedLoading,
                            loadingMore = loadingMoreFeed,
                            hasMore = hasMoreFeed,
                            error = feedError,
                            onLoadMore = { loadMoreFeed() },
                            onOpen = ::openItem,
                        )

                        else -> Column(Modifier.fillMaxSize()) {
                            if (tab == YouTubeTab.CHANNELS) {
                                ChannelSortRow(
                                    selected = channelOrder,
                                    onSelect = { channelOrder = it },
                                )
                            }
                            if (tab == YouTubeTab.HISTORY) {
                                HistoryToolbar(
                                    paused = historyPaused,
                                    message = libraryMessage,
                                    onTogglePause = {
                                        val next = !historyPaused
                                        scope.launch {
                                            runCatching { YouTubeEngine.setHistoryEnabled(!next) }
                                                .onSuccess {
                                                    historyPaused = next
                                                    libraryMessage = if (next) "Watch history is paused." else "Watch history is on."
                                                }
                                                .onFailure { libraryMessage = it.message ?: "Could not change watch history." }
                                        }
                                    },
                                    onClear = { confirmClearHistory = true },
                                )
                            }
                            CategoryFeedPane(
                                modifier = Modifier.weight(1f),
                                title = tab.label,
                                feeds = shownFeeds,
                                loading = feedLoading,
                                loadingMore = loadingMoreFeed,
                                hasMore = hasMoreFeed,
                                error = feedError,
                                onLoadMore = { loadMoreFeed() },
                                onOpen = ::openItem,
                            )
                        }
                    }
                }
            }
            }
        }
    }

    renameDraft?.let { draft ->
        val target = overlay as? YouTubeOverlay.Playlist
        AlertDialog(
            onDismissRequest = { renameDraft = null },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank() && target != null,
                    onClick = {
                        val playlist = target ?: return@TextButton
                        val name = draft.trim()
                        scope.launch {
                            runCatching { YouTubeEngine.renamePlaylist(playlist.playlistId, name) }
                                .onSuccess {
                                    renameDraft = null
                                    overlay = playlist.copy(title = name)
                                    playlistFeed = playlistFeed?.copy(title = name)
                                    libraryRevision++
                                }
                                .onFailure {
                                    renameDraft = null
                                    libraryMessage = it.message ?: "Could not rename this playlist."
                                }
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameDraft = null }) { Text("Cancel") } },
        )
    }

    if (confirmDeletePlaylist) {
        val target = overlay as? YouTubeOverlay.Playlist
        AlertDialog(
            onDismissRequest = { confirmDeletePlaylist = false },
            title = { Text("Delete playlist?") },
            text = {
                Text("\"${target?.title.orEmpty()}\" will be deleted from your YouTube account. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    enabled = target != null,
                    onClick = {
                        val playlistId = target?.playlistId ?: return@TextButton
                        confirmDeletePlaylist = false
                        scope.launch {
                            runCatching { YouTubeEngine.deletePlaylist(playlistId) }
                                .onSuccess {
                                    overlay = null
                                    playlistFeed = null
                                    libraryRevision++
                                }
                                .onFailure { libraryMessage = it.message ?: "Could not delete this playlist." }
                        }
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePlaylist = false }) { Text("Cancel") } },
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear watch history?") },
            text = { Text("Everything you've watched will be removed from your YouTube account history. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearHistory = false
                        scope.launch {
                            runCatching { YouTubeEngine.clearHistory() }
                                .onSuccess {
                                    feeds = emptyList()
                                    libraryMessage = "Watch history cleared."
                                }
                                .onFailure { libraryMessage = it.message ?: "Could not clear watch history." }
                        }
                    },
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") } },
        )
    }

    signInCode?.let { code ->
        AlertDialog(
            onDismissRequest = {
                signInDisposable?.dispose()
                signInDisposable = null
                signInCode = null
            },
            title = { Text("Sign in to YouTube") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Open the activation page on your phone or computer, then enter this code:")
                    Text(
                        code,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                // The Google device page reads the code from the link, so the code is already filled in.
                                uriHandler.openUri("https://www.google.com/device?user_code=${code.filterNot { it.isWhitespace() }}")
                            },
                        ) {
                            Text("Open sign-in page")
                        }
                        TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                            Text("Copy code")
                        }
                    }
                    Text("This window updates automatically when you approve the sign-in.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        signInDisposable?.dispose()
                        signInDisposable = null
                        signInCode = null
                    },
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun MusicMiniPlayer(
    item: YouTubeVideoItem,
    onClose: () -> Unit,
) {
    var playback by remember(item.videoId) { mutableStateOf<YouTubePlayback?>(null) }
    var error by remember(item.videoId) { mutableStateOf<String?>(null) }
    var controller by remember { mutableStateOf<PlayerEngineController?>(null) }
    var snapshot by remember(item.videoId) { mutableStateOf(PlayerPlaybackSnapshot()) }

    LaunchedEffect(item.videoId) {
        playback = null
        error = null
        runCatching { YouTubeEngine.resolvePlayback(item, music = true) }
            .onSuccess { playback = it }
            .onFailure { error = it.message ?: "Could not play this track." }
    }

    Row(
        Modifier.fillMaxWidth()
            .background(YouTubePanel)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.width(52.dp).height(52.dp).clip(RoundedCornerShape(6.dp)).background(YouTubePanelRaised),
        ) {
            DeArrowDesktop.thumbnailUrl(item)?.takeIf { it.isNotBlank() }?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            playback?.let { active ->
                Box(Modifier.size(4.dp)) {
                    key(active.videoUrl) {
                        PlatformPlayerSurface(
                            sourceUrl = active.videoUrl,
                            sourceAudioUrl = active.audioUrl,
                            sourceHeaders = active.headers,
                            modifier = Modifier.size(4.dp),
                            useNativeController = true,
                            onControllerReady = { controller = it },
                            onSnapshot = { snapshot = it },
                            onError = { error = it },
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, maxLines = 1, fontWeight = FontWeight.Bold)
            Text(
                item.author ?: "Music",
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (playback == null && error == null) {
            CircularProgressIndicator(Modifier.width(22.dp).height(22.dp))
        } else if (playback != null) {
            TextButton(onClick = {
                if (snapshot.isPlaying) controller?.pause() else controller?.play()
            }) {
                Text(if (snapshot.isPlaying) "Pause" else "Play")
            }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

@Composable
private fun SignInPrompt(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Sign in to unlock Home, Subscriptions, History, Notifications, Playlists and Channels",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Use the TV activation code flow. No Google Cloud project or API key is needed.",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSignIn) { Text("Sign in") }
    }
}

@Composable
private fun SearchPane(
    modifier: Modifier,
    onOpen: (YouTubeVideoItem) -> Unit,
    initialQuery: String = "",
    searchRequest: Int = 0,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeVideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var typeIndex by remember { mutableStateOf(0) }
    var sortIndex by remember { mutableStateOf(0) }
    var dateIndex by remember { mutableStateOf(0) }
    var durationIndex by remember { mutableStateOf(0) }

    fun searchOptions(): Int =
        searchTypeOptions[typeIndex].second or
            searchSortOptions[sortIndex].second or
            searchDateOptions[dateIndex].second or
            searchDurationOptions[durationIndex].second

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        val options = searchOptions()
        scope.launch {
            loading = true
            error = null
            runCatching { YouTubeEngine.search(q, options) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "Search failed." }
            loading = false
        }
    }

    LaunchedEffect(results) {
        DeArrowDesktop.requestItems(results)
    }

    LaunchedEffect(searchRequest) {
        if (searchRequest > 0 && initialQuery.isNotBlank()) {
            query = initialQuery
            runSearch()
        }
    }

    Column(modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search YouTube") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            )
            Button(onClick = ::runSearch, enabled = query.isNotBlank() && !loading) {
                Text(if (loading) "Searching…" else "Search")
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SearchFilterChip("Type: ${searchTypeOptions[typeIndex].first}", typeIndex > 0) {
                typeIndex = (typeIndex + 1) % searchTypeOptions.size
                runSearch()
            }
            SearchFilterChip("Sort: ${searchSortOptions[sortIndex].first}", sortIndex > 0) {
                sortIndex = (sortIndex + 1) % searchSortOptions.size
                runSearch()
            }
            SearchFilterChip("Date: ${searchDateOptions[dateIndex].first}", dateIndex > 0) {
                dateIndex = (dateIndex + 1) % searchDateOptions.size
                runSearch()
            }
            SearchFilterChip("Length: ${searchDurationOptions[durationIndex].first}", durationIndex > 0) {
                durationIndex = (durationIndex + 1) % searchDurationOptions.size
                runSearch()
            }
            if (typeIndex + sortIndex + dateIndex + durationIndex > 0) {
                TextButton(onClick = {
                    typeIndex = 0
                    sortIndex = 0
                    dateIndex = 0
                    durationIndex = 0
                    runSearch()
                }) { Text("Reset") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (loading) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        Text(
            if (results.isEmpty()) "Search for something to get started." else "${results.size} results",
            style = MaterialTheme.typography.labelMedium,
            color = Color.LightGray,
        )

        // Results as cards, like the other tabs: thumbnails, titles, views and dates.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(260.dp),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(count = results.size, key = { index -> results[index].videoId }) { index ->
                val item = results[index]
                HomeVideoCard(item = item, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun FeedPane(
    modifier: Modifier,
    feeds: List<YouTubeFeed>,
    loading: Boolean,
    error: String?,
    onOpen: (YouTubeVideoItem) -> Unit,
) {
    Box(modifier) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            feeds.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Nothing here yet.", color = Color.LightGray)
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 18.dp,
                    vertical = 16.dp,
                ),
            ) {
                itemsIndexed(feeds, key = { index, feed -> "$index:${feed.title}" }) { _, feed ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (feed.title.isNotBlank()) {
                            Text(
                                feed.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(feed.items, key = { it.videoId }) { item ->
                                VideoCard(item = item, onClick = { onOpen(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationPane(
    modifier: Modifier,
    feeds: List<YouTubeFeed>,
    loading: Boolean,
    error: String?,
    onOpen: (YouTubeVideoItem) -> Unit,
    onHide: (YouTubeVideoItem) -> Unit,
) {
    Box(modifier) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            feeds.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No notifications yet.", color = Color.LightGray)
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(feeds, key = { index, feed -> "$index:${feed.title}" }) { _, feed ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (feed.title.isNotBlank()) {
                            Text(feed.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        feed.items.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                VideoRow(item = item, onClick = { onOpen(item) })
                                TextButton(onClick = { onHide(item) }) {
                                    Text("Hide notification", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeThumbnail(
    item: YouTubeVideoItem,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val pointerHovered by hoverSource.collectIsHoveredAsState()
    val windowInfo = LocalWindowInfo.current
    val hovered = pointerHovered && windowInfo.isWindowFocused
    val animatedPreview = item.previewUrl?.takeIf { hovered }
    val hoverScale by animateFloatAsState(
        targetValue = if (hovered) 1.045f else 1f,
        animationSpec = tween(durationMillis = 3600),
        label = "youtube-thumb-hover",
    )

    Box(modifier) {
        NuvioAsyncImage(
            model = animatedPreview ?: DeArrowDesktop.thumbnailUrl(item),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    scaleX = hoverScale
                    scaleY = hoverScale
                    transformOrigin = TransformOrigin.Center
                }
                .hoverable(hoverSource),
            contentScale = contentScale,
            desktopAnimation = if (animatedPreview != null) NuvioDesktopAnimation.Always else NuvioDesktopAnimation.Off,
        )

        // Inline video previews are intentionally not used here: the only desktop
        // video path is mpv in a separate child window, which cannot be composited
        // with Compose and flickers while the grid repaints. A hovered card keeps
        // its still and shows a play affordance instead.
        if (hovered && animatedPreview == null) {
            Box(
                Modifier.align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA08080C)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadMoreRow(
    triggerKey: Int,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(triggerKey) { onLoadMore() }
    Box(
        Modifier.fillMaxWidth().padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loadingMore) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = YouTubeRed,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun HomeFeedPane(
    modifier: Modifier,
    feeds: List<YouTubeFeed>,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    onOpen: (YouTubeVideoItem) -> Unit,
) {
    val items = remember(feeds) {
        feeds.flatMap { it.items }
            .filter { it.kind == YouTubeItemKind.VIDEO }
            .distinctBy { it.videoId }
    }
    Box(modifier) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = YouTubeRed)
            }

            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Nothing here yet.", color = Color.LightGray)
            }

            else -> LazyVerticalGrid(
                columns = oneTubeGridCells(252.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "home-hero") {
                    HomeHeroRow(
                        hero = items.first(),
                        trending = items.drop(1).take(4),
                        onOpen = onOpen,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "home-recommended") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Recommended",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${items.size} videos",
                            style = MaterialTheme.typography.labelMedium,
                            color = YouTubeTextDim,
                        )
                    }
                }
                items(
                    count = items.size,
                    key = { index -> "home:${items[index].videoId}" },
                ) { index ->
                    HomeVideoCard(item = items[index], onClick = { onOpen(items[index]) })
                }
                if (hasMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "home-load-more") {
                        LoadMoreRow(triggerKey = items.size, loadingMore = loadingMore, onLoadMore = onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroRow(
    hero: YouTubeVideoItem,
    trending: List<YouTubeVideoItem>,
    onOpen: (YouTubeVideoItem) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(356.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier.weight(1.75f).fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(YouTubePanel)
                .clickable { onOpen(hero) },
        ) {
            YouTubeThumbnail(
                item = hero,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x11000000),
                            Color(0xAA05050A),
                            Color(0xF208080C),
                        ),
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(26.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "FEATURED � NUVIO DESKTOP",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.3.sp),
                    color = Color(0xFFFF8FA3),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    DeArrowDesktop.title(hero),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildList {
                        videoMeta(hero).takeIf { it.isNotBlank() }?.let(::add)
                        formatDuration(hero.durationSeconds).takeIf { it.isNotBlank() }?.let(::add)
                    }.joinToString(" \u00B7 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB8B8C4),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { onOpen(hero) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YouTubeRed,
                            contentColor = Color.White,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 17.dp,
                            vertical = 9.dp,
                        ),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Watch now", fontWeight = FontWeight.SemiBold)
                    }
                    if (hero.percentWatched > 0) {
                        Text(
                            "${hero.percentWatched}% watched",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC8C8D2),
                        )
                    }
                }
            }
            if (hero.isLive || hero.durationSeconds > 0) {
                Text(
                    if (hero.isLive) "LIVE" else formatDuration(hero.durationSeconds),
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(14.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (hero.percentWatched > 0) {
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x33FFFFFF)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(hero.percentWatched.coerceIn(0, 100) / 100f)
                            .fillMaxHeight()
                            .background(YouTubeRed),
                    )
                }
            }
        }

        Column(
            Modifier.weight(0.72f).fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(YouTubePanel)
                .padding(16.dp),
        ) {
            Text(
                "Trending now",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
            trending.forEach { item ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onOpen(item) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(7.dp)).background(YouTubePanelRaised)) {
                        YouTubeThumbnail(
                            item = item,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (item.isLive || item.durationSeconds > 0) {
                            Text(
                                if (item.isLive) "LIVE" else formatDuration(item.durationSeconds),
                                modifier = Modifier.align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            DeArrowDesktop.title(item),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.metadata?.takeIf { it.isNotBlank() } ?: item.author.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = YouTubeTextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Picked from your subscriptions",
                style = MaterialTheme.typography.labelSmall,
                color = YouTubeTextDim,
            )
        }
    }
}

@Composable
private fun HomeVideoCard(item: YouTubeVideoItem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(YouTubePanel),
        ) {
            YouTubeThumbnail(
                item = item,
                modifier = Modifier.fillMaxSize(),
            )
            val label = when {
                item.isLive -> "LIVE"
                item.durationSeconds > 0 -> formatDuration(item.durationSeconds)
                else -> item.badgeText?.takeIf { it.isNotBlank() }
            }
            label?.let { badge ->
                Text(
                    label,
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                )
            }
            if (item.percentWatched > 0) {
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x33FFFFFF)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(item.percentWatched.coerceIn(0, 100) / 100f)
                            .fillMaxHeight()
                            .background(YouTubeRed),
                    )
                }
            }
        }
        Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(YouTubePanelRaised),
                contentAlignment = Alignment.Center,
            ) {
                // A channel card shows its own art in the circle; other cards show the author's initial.
                val channelArt = item.thumbnailUrl?.takeIf { item.kind == YouTubeItemKind.CHANNEL && it.isNotBlank() }
                if (channelArt != null) {
                    AsyncImage(
                        model = channelArt,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        item.author?.trim()?.take(1)?.uppercase(Locale.US).orEmpty().ifBlank { "?" },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFD5D5DF),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    DeArrowDesktop.title(item),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = videoMeta(item)
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFeedPane(
    modifier: Modifier,
    title: String,
    feeds: List<YouTubeFeed>,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    onOpen: (YouTubeVideoItem) -> Unit,
) {
    val items = remember(feeds) {
        feeds.flatMap { it.items }.distinctBy { "${it.kind}:${it.videoId}" }
    }
    Box(modifier) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = YouTubeRed)
            }

            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Nothing here yet.", color = Color.LightGray)
            }

            else -> LazyVerticalGrid(
                columns = oneTubeGridCells(252.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "category-header") {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${items.size} ${if (items.size == 1) "item" else "items"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = YouTubeTextDim,
                        )
                    }
                }
                items(
                    count = items.size,
                    key = { index -> "category:${items[index].kind}:${items[index].videoId}" },
                ) { index ->
                    HomeVideoCard(item = items[index], onClick = { onOpen(items[index]) })
                }
                if (hasMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "category-load-more") {
                        LoadMoreRow(triggerKey = items.size, loadingMore = loadingMore, onLoadMore = onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsFeedPane(
    modifier: Modifier,
    feeds: List<YouTubeFeed>,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    onOpen: (YouTubeVideoItem) -> Unit,
) {
    val all = remember(feeds) { feeds.flatMap { it.items }.distinctBy { "${it.kind}:${it.videoId}" } }
    val items = remember(all) { all.filter { it.isShorts }.ifEmpty { all } }
    Box(modifier) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = YouTubeRed)
            }

            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No Shorts yet.", color = Color.LightGray)
            }

            else -> LazyVerticalGrid(
                columns = oneTubeGridCells(172.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "shorts-header") {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Shorts",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${items.size} clips",
                            style = MaterialTheme.typography.labelMedium,
                            color = YouTubeTextDim,
                        )
                    }
                }
                items(count = items.size, key = { index -> "short:${items[index].kind}:${items[index].videoId}" }) { index ->
                    ShortsCard(item = items[index], onClick = { onOpen(items[index]) })
                }
                if (hasMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "shorts-load-more") {
                        LoadMoreRow(triggerKey = items.size, loadingMore = loadingMore, onLoadMore = onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsCard(item: YouTubeVideoItem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(14.dp))
                .background(YouTubePanel),
        ) {
            YouTubeThumbnail(
                item = item,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x22000000), Color(0xDD08080C)),
                    ),
                ),
            )
            if (item.isLive) {
                Text(
                    "LIVE",
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(YouTubeRed, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                item.title,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            item.author.orEmpty(),
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = YouTubeTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MusicFeedPane(
    modifier: Modifier,
    feeds: List<YouTubeFeed>,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    onOpen: (YouTubeVideoItem) -> Unit,
) {
    val all = remember(feeds) { feeds.flatMap { it.items }.distinctBy { "${it.kind}:${it.videoId}" } }
    val items = remember(all) { all.filter { it.isMusic }.ifEmpty { all } }
    Box(modifier) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = YouTubeRed)
            }

            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Nothing here yet.", color = Color.LightGray)
            }

            else -> LazyVerticalGrid(
                columns = oneTubeGridCells(190.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "music-header") {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Music",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${items.size} tracks",
                            style = MaterialTheme.typography.labelMedium,
                            color = YouTubeTextDim,
                        )
                    }
                }
                items(count = items.size, key = { index -> "music:${items[index].kind}:${items[index].videoId}" }) { index ->
                    MusicFeedCard(item = items[index], onClick = { onOpen(items[index]) })
                }
                if (hasMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "music-load-more") {
                        LoadMoreRow(triggerKey = items.size, loadingMore = loadingMore, onLoadMore = onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicFeedCard(item: YouTubeVideoItem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(YouTubePanel),
        ) {
            YouTubeThumbnail(
                item = item,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x44000000))),
                ),
            )
            Text(
                "MUSIC",
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            item.title,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.author.orEmpty(),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = YouTubeTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FeedGridPane(
    modifier: Modifier,
    feeds: List<YouTubeFeed>,
    loading: Boolean,
    error: String?,
    onOpen: (YouTubeVideoItem) -> Unit,
) {
    val items = remember(feeds) { feeds.flatMap { it.items } }
    Box(modifier) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Nothing here yet.", color = Color.LightGray)
            }

            else -> LazyVerticalGrid(
                columns = oneTubeGridCells(210.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(
                    count = items.size,
                    key = { index -> "$index:${items[index].kind}:${items[index].videoId}" },
                ) { index ->
                    VideoCard(item = items[index], onClick = { onOpen(items[index]) })
                }
            }
        }
    }
}

@Composable
private fun ChannelPane(
    modifier: Modifier,
    overlay: YouTubeOverlay.Channel,
    feeds: List<YouTubeFeed>,
    loading: Boolean,
    error: String?,
    subscribed: Boolean,
    subscriberCount: String?,
    signedIn: Boolean,
    actionMessage: String?,
    onBack: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onOpen: (YouTubeVideoItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember(overlay.channelId) { mutableStateOf("") }
    var searchFeed by remember(overlay.channelId) { mutableStateOf<YouTubeFeed?>(null) }
    var searchLoading by remember(overlay.channelId) { mutableStateOf(false) }
    var searchError by remember(overlay.channelId) { mutableStateOf<String?>(null) }
    var sortOptions by remember(overlay.channelId) { mutableStateOf<List<YouTubeChannelSort>>(emptyList()) }
    var sortedFeed by remember(overlay.channelId) { mutableStateOf<YouTubeFeed?>(null) }
    var selectedSortTitle by remember(overlay.channelId) { mutableStateOf<String?>(null) }
    var sortLoading by remember(overlay.channelId) { mutableStateOf(false) }

    LaunchedEffect(overlay.channelId) {
        runCatching { YouTubeEngine.channelSorting(overlay.channelId) }
            .onSuccess { sortOptions = it }
    }

    LaunchedEffect(feeds, searchFeed, sortedFeed) {
        DeArrowDesktop.requestItems(
            feeds.flatMap { it.items } + searchFeed?.items.orEmpty() + sortedFeed?.items.orEmpty(),
        )
    }

    fun selectSort(option: YouTubeChannelSort) {
        if (sortLoading) return
        scope.launch {
            sortLoading = true
            runCatching { YouTubeEngine.channelSortGroup(option.reloadPageKey) }
                .onSuccess {
                    sortedFeed = it
                    selectedSortTitle = option.title
                }
                .onFailure { searchError = it.message ?: "Could not sort this channel." }
            sortLoading = false
        }
    }

    fun runChannelSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty() || searchLoading) return
        scope.launch {
            searchLoading = true
            searchError = null
            runCatching { YouTubeEngine.channelSearch(overlay.channelId, query) }
                .onSuccess { searchFeed = it }
                .onFailure { searchError = it.message ?: "Channel search failed." }
            searchLoading = false
        }
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Column(Modifier.weight(1f)) {
                Text(
                    overlay.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subscriberCount?.takeIf { it.isNotBlank() }?.let {
                    Text("$it subscribers", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                }
            }
            Button(onClick = onToggleSubscribe) {
                Text(if (subscribed) "Subscribed" else "Subscribe")
            }
        }
        actionMessage?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = Color(0xFF9AE6B4),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search this channel") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runChannelSearch() }),
            )
            Button(onClick = ::runChannelSearch, enabled = searchQuery.isNotBlank() && !searchLoading) {
                Text(if (searchLoading) "Searching�" else "Search")
            }
            if (searchFeed != null) {
                TextButton(onClick = {
                    searchFeed = null
                    searchError = null
                }) { Text("Clear") }
            }
        }
        searchError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 18.dp))
        }
        if (sortOptions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = sortedFeed == null,
                    onClick = {
                        sortedFeed = null
                        selectedSortTitle = null
                    },
                    label = { Text("Default") },
                )
                sortOptions.forEach { option ->
                    FilterChip(
                        selected = selectedSortTitle == option.title,
                        onClick = { selectSort(option) },
                        label = { Text(option.title) },
                        enabled = !sortLoading,
                    )
                }
            }
        }
        val displayedFeeds = sortedFeed?.let { listOf(it) } ?: feeds
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            displayedFeeds.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("This channel has no public videos.", color = Color.LightGray)
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                searchFeed?.takeIf { it.items.isNotEmpty() }?.let { feed ->
                    item(key = "channel-search") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Search results",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(feed.items, key = { "search:${it.kind}:${it.videoId}" }) { item ->
                                    VideoCard(item = item, onClick = { onOpen(item) })
                                }
                            }
                        }
                    }
                }
                itemsIndexed(displayedFeeds, key = { index, feed -> "$index:${feed.title}" }) { _, feed ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (feed.title.isNotBlank()) {
                            Text(
                                feed.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(
                                feed.items,
                                key = { index, item -> "$index:${item.kind}:${item.videoId}" },
                            ) { _, item ->
                                VideoCard(item = item, onClick = { onOpen(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPane(
    modifier: Modifier,
    overlay: YouTubeOverlay.Playlist,
    feed: YouTubeFeed?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpen: (YouTubeVideoItem, List<YouTubeVideoItem>) -> Unit,
    onPlayQueue: (List<YouTubeVideoItem>) -> Unit,
    canEdit: Boolean = false,
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRemoveItem: (YouTubeVideoItem) -> Unit = {},
) {
    val queue = feed?.items.orEmpty().filter { it.kind == YouTubeItemKind.VIDEO }
    LaunchedEffect(feed) {
        DeArrowDesktop.requestItems(feed?.items.orEmpty())
    }
    // Views and post date for each video, fetched once per video: the playlist and channel listings don't carry them.
    val videoStats = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(feed) {
        val missing = feed?.items.orEmpty()
            .filter { it.kind == YouTubeItemKind.VIDEO && it.videoId !in videoStats }
            .map { it.videoId }
            .distinct()
            .take(40)
        kotlinx.coroutines.coroutineScope {
            missing.forEach { id ->
                launch {
                    YouTubeEngine.videoStats(id)?.let { videoStats[id] = it }
                }
            }
        }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                feed?.title?.takeIf { it.isNotBlank() } ?: overlay.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (canEdit) {
                TextButton(onClick = onRename) { Text("Rename") }
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
            Button(
                onClick = { onPlayQueue(queue) },
                enabled = queue.isNotEmpty(),
            ) {
                Text("Play all")
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            feed == null || feed.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("This playlist has no videos.", color = Color.LightGray)
            }

            else -> LazyVerticalGrid(
                GridCells.Adaptive(240.dp),
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(count = feed.items.size) { index ->
                    val item = feed.items[index]
                    // Views and post date from the lookup replace the badge-only line the listing gives.
                    val shown = videoStats[item.videoId]?.let { item.copy(metadata = it) } ?: item
                    Column {
                        HomeVideoCard(item = shown, onClick = { onOpen(item, queue) })
                        if (canEdit && item.kind == YouTubeItemKind.VIDEO) {
                            TextButton(onClick = { onRemoveItem(item) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Four videos at once, in a 2x2 grid, like Live TV's multiview. Each tile picks a video from the list
 * on screen; only the tile with sound plays audio. The buttons sit under each tile, as in Live TV.
 */
@Composable
private fun YouTubeMultiview(
    candidates: List<YouTubeVideoItem>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var tiles by remember { mutableStateOf<List<PlaybackTarget?>>(List(4) { null }) }
    var soundIndex by remember { mutableStateOf(0) }
    var pickingFor by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    // The video windows sit above Compose, so they step aside while the picker is open.
    com.nuvio.app.core.ui.CoverNativeVideo(covering = pickingFor != null)

    Column(modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Multiview", color = Color.LightGray, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close multiview") }
        }
        listOf(0, 2).forEach { first ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(first, first + 1).forEach { index ->
                    MultiviewTile(
                        target = tiles[index],
                        hasSound = index == soundIndex && tiles[index] != null,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onSound = { soundIndex = index },
                        onChoose = {
                            query = ""
                            pickingFor = index
                        },
                        chooseLabel = "Search",
                        emptyLabel = "Search videos",
                        showPause = true,
                    )
                }
            }
        }
    }

    pickingFor?.let { slot ->
        YouTubeTileSearch(
            onDismiss = { pickingFor = null },
            onPick = { item ->
                pickingFor = null
                scope.launch {
                    runCatching {
                        // Through the stream proxy, as the OneTube player's streams are: YouTube throttles
                        // a plain download, which left some tiles stalled.
                        YouTubeStreamProxy.wrapPlayback(
                            YouTubeEngine.resolvePlayback(item, music = false, maxHeight = 720),
                        )
                    }.onSuccess { playback ->
                        tiles = tiles.toMutableList().also {
                            it[slot] = PlaybackTarget(
                                url = playback.videoUrl,
                                headers = playback.headers,
                                title = playback.title,
                                audioUrl = playback.audioUrl,
                            )
                        }
                    }
                }
            },
        )
    }
}

/** A YouTube search with thumbnail cards, like the Search tab. Picking a card puts that video in the tile. */
@Composable
private fun YouTubeTileSearch(
    onDismiss: () -> Unit,
    onPick: (YouTubeVideoItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeVideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    fun runSearch() {
        val text = query.trim()
        if (text.isEmpty()) return
        scope.launch {
            loading = true
            error = null
            // The same plain search the Search tab uses, which returns about 100 results; only videos are kept.
            runCatching { YouTubeEngine.search(text) }
                .onSuccess { found ->
                    results = found.filter { it.kind == YouTubeItemKind.VIDEO && !it.isLive }
                    searched = true
                }
                .onFailure { error = it.message ?: "Search failed." }
            loading = false
        }
    }

    // The window is sized to this panel (not the platform default), with a margin around it.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(32.dp).width(1120.dp).height(640.dp).background(YouTubePanel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search YouTube") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { runSearch() }) { Text("Search") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)

                results.isEmpty() -> Text(
                    if (searched) "No videos found." else "Type a search to find a video.",
                    color = Color.LightGray,
                )

                else -> LazyVerticalGrid(
                    GridCells.Fixed(4),
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = results.size) { index ->
                        val item = results[index]
                        HomeVideoCard(item = item, onClick = { onPick(item) })
                    }
                }
            }
        }
    }
}

/**
 * A slider through the Shorts on screen: the thumb sits on the current Short, and letting go of it opens the
 * Short at that position. Dragging left goes back, dragging right goes forward.
 */
@Composable
private fun ShortsQueueSlider(index: Int, count: Int, onJump: (Int) -> Unit) {
    if (count < 2 || index < 0) return
    var position by remember(index, count) { mutableStateOf(index.toFloat()) }
    androidx.compose.material3.Slider(
        value = position,
        onValueChange = { position = it },
        onValueChangeFinished = { onJump(Math.round(position)) },
        valueRange = 0f..(count - 1).toFloat(),
        steps = count - 2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
    )
}

/**
 * The subtitle tracks to offer, like the TV app: only the viewer's preferred languages. With no preference set,
 * or none of the video's tracks match, every track is offered so the menu is never empty.
 */
private fun oneTubeVisibleSubtitles(tracks: List<YouTubeSubtitle>): List<YouTubeSubtitle> {
    // Every track is offered for now: filtering by the language preference hid English on some videos.
    return tracks
}

/** A fixed colour per chat author, so the same person always shows in the same colour. */
private val ChatNameColors = listOf(
    Color(0xFF4EA1FF),
    Color(0xFF34D399),
    Color(0xFFFBBF24),
    Color(0xFFF472B6),
    Color(0xFFA78BFA),
    Color(0xFF22D3EE),
    Color(0xFFFB923C),
    Color(0xFF84CC16),
)

private fun chatNameColor(author: String): Color =
    ChatNameColors[(author.hashCode() and 0x7fffffff) % ChatNameColors.size]

/** Sort chips above the subscribed-channels grid. */
@Composable
private fun ChannelSortRow(selected: YouTubeChannelOrder, onSelect: (YouTubeChannelOrder) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            YouTubeChannelOrder.DEFAULT to "Default",
            YouTubeChannelOrder.NEW_CONTENT to "New content",
            YouTubeChannelOrder.NAME to "Name",
            YouTubeChannelOrder.LAST_VIEWED to "Last viewed",
        ).forEach { (order, label) ->
            FilterChip(
                selected = selected == order,
                onClick = { onSelect(order) },
                label = { Text(label) },
            )
        }
    }
}

/** Pause/resume recording and clear the signed-in account's watch history. */
@Composable
private fun HistoryToolbar(
    paused: Boolean,
    message: String?,
    onTogglePause: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = paused,
            onClick = onTogglePause,
            label = { Text(if (paused) "Resume watch history" else "Pause watch history") },
        )
        TextButton(onClick = onClear) { Text("Clear watch history", color = MaterialTheme.colorScheme.error) }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = YouTubeTextMuted,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun YouTubeAccountAvatar(account: YouTubeAccountItem?, size: Int = 22) {
    val dimension = size.dp
    val url = account?.avatarUrl
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.size(dimension).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(dimension)
                .clip(CircleShape)
                .background(Color(0xFF3A3A44)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                account?.name?.trim()?.take(1)?.uppercase().orEmpty().ifBlank { "?" },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun VideoRow(item: YouTubeVideoItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(YouTubePanel)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeThumbnail(
            item = item,
            modifier = Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(6.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                DeArrowDesktop.title(item),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            val meta = buildList {
                when (item.kind) {
                    YouTubeItemKind.CHANNEL -> add("Channel")
                    YouTubeItemKind.PLAYLIST -> add("Playlist")
                    YouTubeItemKind.VIDEO -> Unit
                }
                item.author?.takeIf { it.isNotBlank() }?.let(::add)
                formatDuration(item.durationSeconds).takeIf { it.isNotBlank() }?.let(::add)
            }
            Text(
                meta.joinToString(" \u00B7 "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
            )
        }
    }
}

@Composable
private fun VideoCard(item: YouTubeVideoItem, onClick: () -> Unit) {
    Column(
        Modifier.width(220.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    ) {
        Box {
            YouTubeThumbnail(
                item = item,
                modifier = Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(8.dp)),
            )
            if (item.isLive || item.durationSeconds > 0) {
                Text(
                    if (item.isLive) "LIVE" else formatDuration(item.durationSeconds),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            DeArrowDesktop.title(item),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            item.author.orEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray,
        )
        if (item.kind != YouTubeItemKind.VIDEO) {
            Text(
                text = if (item.kind == YouTubeItemKind.CHANNEL) "Channel" else "Playlist",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.kind == YouTubeItemKind.CHANNEL) Color(0xFF8AB4F8) else Color(0xFFF6C177),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun VideoDetailPane(
    modifier: Modifier,
    music: Boolean,
    resumePlayback: Boolean,
    preferredQuality: String,
    preferredAudioLanguage: String,
    storyboardPreviews: Boolean,
    liveChatEnabled: Boolean,
    selected: YouTubeVideoItem,
    details: YouTubeDetails?,
    playback: YouTubePlayback?,
    playbackError: String?,
    detailError: String?,
    resolving: Boolean,
    comments: List<YouTubeComment>,
    commentsNewest: Boolean,
    canSortComments: Boolean,
    onCommentSortChange: (Boolean) -> Unit,
    queueNext: YouTubeVideoItem?,
    onQueueNext: () -> Unit,
    queuePrevious: YouTubeVideoItem? = null,
    onQueuePrevious: () -> Unit = {},
    queueIndex: Int = -1,
    queueCount: Int = 0,
    onQueueJump: (Int) -> Unit = {},
    nextCommentsKey: String?,
    commentsLoading: Boolean,
    commentsError: String?,
    chatMessages: List<ChatItem>,
    signedIn: Boolean,
    actionMessage: String?,
    onBack: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onPlaybackError: (String?) -> Unit,
    onOpen: (YouTubeVideoItem) -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onSubscribe: () -> Unit,
    onNotificationState: (NotificationState) -> Unit,
    onOpenChannel: (String, String) -> Unit,
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    skipSponsors: Boolean,
    onSkipSponsorsChange: (Boolean) -> Unit,
    onToggleCommentLike: (YouTubeComment) -> Unit,
    onToggleCommentDislike: (YouTubeComment) -> Unit,
) {
    var playerController by remember { mutableStateOf<PlayerEngineController?>(null) }
    var selectedSubtitle by remember(playback) { mutableStateOf<YouTubeSubtitle?>(null) }
    var playbackEnded by remember(selected.videoId) { mutableStateOf(false) }
    var descriptionExpanded by remember(selected.videoId) { mutableStateOf(false) }
    var notificationsMenuOpen by remember(selected.videoId) { mutableStateOf(false) }
    // "Save" dialog: the account's playlists, with this video's membership.
    var saveMenuOpen by remember(selected.videoId) { mutableStateOf(false) }
    var saveChoices by remember(selected.videoId) { mutableStateOf<List<YouTubePlaylistChoice>?>(null) }
    var saveChoicesError by remember(selected.videoId) { mutableStateOf<String?>(null) }
    var newPlaylistName by remember(selected.videoId) { mutableStateOf("") }
    LaunchedEffect(saveMenuOpen) {
        if (!saveMenuOpen) return@LaunchedEffect
        saveChoicesError = null
        saveChoices = null
        runCatching { YouTubeEngine.playlistChoices(selected.videoId) }
            .onSuccess { saveChoices = it }
            .onFailure { saveChoicesError = it.message ?: "Could not load your playlists." }
    }
    val saveScope = rememberCoroutineScope()
    fun togglePlaylistMembership(choice: YouTubePlaylistChoice) {
        saveScope.launch {
            runCatching {
                if (choice.containsVideo) {
                    YouTubeEngine.removeFromPlaylist(choice.id, selected.videoId)
                } else {
                    YouTubeEngine.addToPlaylist(choice.id, selected.videoId)
                }
            }
                .onSuccess {
                    saveChoices = saveChoices?.map {
                        if (it.id == choice.id) it.copy(containsVideo = !choice.containsVideo) else it
                    }
                }
                .onFailure { saveChoicesError = it.message ?: "Could not update this playlist." }
        }
    }
    val clipboard = LocalClipboardManager.current
    val isLive = details?.isLive == true || selected.isLive
    val scope = rememberCoroutineScope()
    val resumePositionMs = remember(selected.videoId, resumePlayback) {
        if (!resumePlayback) {
            0L
        } else {
            val percent = selected.percentWatched
            val seconds = selected.startTimeSeconds.takeIf { it > 0 }
                ?: if (percent > 0 && selected.durationMs > 0) {
                    (selected.durationMs * percent / 100_000L).toInt()
                } else {
                    0
                }
            val maxStartMs = selected.durationMs.takeIf { it > 0 }?.minus(5_000L) ?: Long.MAX_VALUE
            if (seconds > 5 && seconds * 1000L < maxStartMs) seconds * 1000L else 0L
        }
    }
    var lastSavedPositionMs by remember(selected.videoId) { mutableStateOf(resumePositionMs) }
    var repliesByKey by remember(selected.videoId) {
        mutableStateOf<Map<String, List<YouTubeComment>>>(emptyMap())
    }
    var repliesLoading by remember(selected.videoId) { mutableStateOf<Set<String>>(emptySet()) }
    var qualityPlayback by remember(selected.videoId) { mutableStateOf<YouTubePlayback?>(null) }
    var selectedQualityHeight by remember(selected.videoId) {
        mutableStateOf(YouTubeSettingsStore.qualityHeight(preferredQuality))
    }
    var qualityLoading by remember(selected.videoId) { mutableStateOf(false) }
    var qualityError by remember(selected.videoId) { mutableStateOf<String?>(null) }
    var showQualityMenu by remember(selected.videoId) { mutableStateOf(false) }
    val playbackPosition = remember(selected.videoId) { PlaybackPosition(resumePositionMs) }
    var playbackSpeed by remember(selected.videoId) { mutableStateOf(1f) }
    var pipAfterFullscreen by remember(selected.videoId) { mutableStateOf(false) }
    var startPositionMs by remember(selected.videoId) { mutableStateOf(resumePositionMs) }
    var qualityRevision by remember(selected.videoId) { mutableStateOf(0) }
    var sponsorSegments by remember(selected.videoId) { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    val activePlayback = qualityPlayback ?: playback
    com.nuvio.app.core.ui.CoverNativeVideo(notificationsMenuOpen)
    // The Save dialog is drawn above Compose, so the native video must step aside while it's open.
    com.nuvio.app.core.ui.CoverNativeVideo(saveMenuOpen)
    // One player at a time: a video here stops the Music audio, which stays paused until resumed there.
    DisposableEffect(activePlayback?.videoUrl) {
        MusicShellState.otherVideoActive = activePlayback != null
        onDispose { MusicShellState.otherVideoActive = false }
    }
    LaunchedEffect(activePlayback?.videoUrl) {
        if (activePlayback != null && com.nuvio.app.features.settings.MediaInterruptSettings.stopMusicOnOtherMedia) {
            MusicPlaybackSession.controller?.pause()
        }
    }
    // Settings → Automatically select subtitles: pick the preferred language
    // (a real track over an auto-generated one) when the video has it.
    LaunchedEffect(activePlayback?.videoUrl, YouTubeSettingsStore.autoSubtitles) {
        if (!YouTubeSettingsStore.autoSubtitles || selectedSubtitle != null) return@LaunchedEffect
        val wanted = YouTubeSettingsStore.subtitleLanguage.lowercase()
        val subs = activePlayback?.subtitles.orEmpty().filter { it.language.lowercase().startsWith(wanted) }
        selectedSubtitle = subs.firstOrNull { !it.isAutoGenerated } ?: subs.firstOrNull()
    }
    // SponsorBlock: a marked part waiting for "Skip" (ask mode), parts the
    // viewer chose to watch, and the short notice after an automatic skip.
    var sponsorPrompt by remember(selected.videoId) { mutableStateOf<SponsorSegment?>(null) }
    val sponsorDeclined = remember(selected.videoId) { mutableSetOf<Long>() }
    var sponsorNotice by remember(selected.videoId) { mutableStateOf<String?>(null) }
    LaunchedEffect(sponsorNotice) {
        if (sponsorNotice != null) {
            delay(2_500L)
            sponsorNotice = null
        }
    }

    LaunchedEffect(selected.videoId, skipSponsors) {
        if (skipSponsors) {
            runCatching { YouTubeEngine.sponsorSegments(selected.videoId) }
                .onSuccess { sponsorSegments = it }
        } else {
            sponsorSegments = emptyList()
        }
    }

    LaunchedEffect(selected.videoId) {
        DeArrowDesktop.requestIds(listOf(selected.videoId))
    }

    var storyboard by remember(selected.videoId) { mutableStateOf<YouTubeStoryboard?>(null) }

    // The video's length: from the list it was opened from, else (search results, links) from the player once it knows.
    var playerDurationMs by remember(selected.videoId) { mutableStateOf(0L) }
    val storyboardDurationMs = selected.durationMs.takeIf { it > 0 } ?: playerDurationMs

    LaunchedEffect(selected.videoId, storyboardPreviews, playback?.videoUrl, storyboardDurationMs > 0) {
        storyboard = null
        playerController?.nativePlayer()?.updateStoryboard(null)
        // Waits for the video: the engine handles one lookup at a time, so this must not queue ahead of the stream.
        if (playback != null && storyboardPreviews && !isLive && storyboardDurationMs > 0) {
            storyboard = runCatching {
                YouTubeEngine.storyboard(selected.videoId, storyboardDurationMs)
            }.getOrNull()
            OneTubeDebugLog.line("storyboard ${if (storyboard != null) "ready sheets=${storyboard?.groupUrls?.size}" else "unavailable"} durationMs=$storyboardDurationMs")
        } else if (playback != null) {
            OneTubeDebugLog.line("storyboard skipped enabled=$storyboardPreviews live=$isLive durationMs=$storyboardDurationMs")
        }
    }

    LaunchedEffect(playerController, storyboard) {
        playerController?.nativePlayer()?.updateStoryboard(storyboard?.toControlsJson())
    }

    fun changeQuality(height: Int?) {
        if (qualityLoading) return
        qualityLoading = true
        qualityError = null
        startPositionMs = playbackPosition.ms
        qualityRevision += 1
        scope.launch {
            runCatching { YouTubeEngine.resolvePlayback(selected, music, height) }
                .onSuccess {
                    qualityPlayback = it
                    selectedQualityHeight = height
                }
                .onFailure { qualityError = it.message ?: "Could not change quality." }
            qualityLoading = false
        }
    }

    fun loadReplies(comment: YouTubeComment) {
        val key = comment.replyKey ?: return
        if (key in repliesLoading || repliesByKey.containsKey(key)) return
        repliesLoading = repliesLoading + key
        scope.launch {
            runCatching { YouTubeEngine.comments(key) }
                .onSuccess { page -> repliesByKey = repliesByKey + (key to page.comments) }
            repliesLoading = repliesLoading - key
        }
    }
    val nextItem = queueNext
        ?: details?.nextVideo
            ?.takeIf { it.videoId != selected.videoId && it.kind == YouTubeItemKind.VIDEO }
        ?: details?.suggestions?.firstOrNull {
            it.videoId != selected.videoId && it.kind == YouTubeItemKind.VIDEO
        }

    val subtitleLanguages = buildList {
        add(
            PlayerControlSubtitleLanguageItem(
                key = "__off__",
                label = "Off",
                count = 0,
                isSelected = selectedSubtitle == null,
            ),
        )
        oneTubeVisibleSubtitles(activePlayback?.subtitles.orEmpty())
            .groupBy { it.language }
            .forEach { (language, tracks) ->
                add(
                    PlayerControlSubtitleLanguageItem(
                        key = language,
                        label = tracks.firstOrNull()?.name ?: language,
                        count = tracks.size,
                        isSelected = selectedSubtitle?.language == language,
                    ),
                )
            }
    }
    val subtitleOptions = oneTubeVisibleSubtitles(activePlayback?.subtitles.orEmpty()).mapIndexed { index, subtitle ->
        PlayerControlSubtitleOptionItem(
            id = "yt-$index",
            languageKey = subtitle.language,
            kind = "addon",
            index = index,
            sourceLabel = "YouTube",
            title = subtitle.name ?: subtitle.language,
            metadata = if (subtitle.isAutoGenerated) "Auto-generated" else "",
            isSelected = selectedSubtitle?.url == subtitle.url,
        )
    }
    // Quality in the player bar: Auto, then each height the video has (highest first).
    val qualityHeights = activePlayback?.availableVideoHeights.orEmpty().distinct().sortedDescending()
    val qualityLabel = selectedQualityHeight?.let { "${it}p" } ?: "Auto"
    val nextVideo = queueNext ?: nextItem
    // Chapters in the player bar (button, list, seek-bar marks); none for live streams.
    val videoChapters = if (isLive) emptyList() else details?.chapters.orEmpty()
    val playerSettings by PlayerSettingsRepository.uiState.collectAsState()
    val appFullscreen = isFullscreenActionActive()
    var subtitleDelayMs by remember { mutableStateOf(0) }
    var subtitleFontsRevision by remember { mutableStateOf(0) }
    var subtitleFontImportRequest by remember { mutableStateOf(0) }
    var subtitleFontImportFailed by remember { mutableStateOf(false) }
    val subtitleFontItems = remember(subtitleFontsRevision) {
        BuiltInSubtitleFonts.map { id ->
            PlayerControlSubtitleFont(
                id = id,
                label = when (id) {
                    "default" -> "Default"
                    "sans" -> "Sans"
                    "serif" -> "Serif"
                    "mono" -> "Mono"
                    else -> id
                },
            )
        } + SubtitleFonts.installed().map { font ->
            PlayerControlSubtitleFont(font.id, font.name)
        }
    }
    val importSubtitleFont = rememberSubtitleFontImporter { result ->
        result.onSuccess {
            subtitleFontImportFailed = false
            subtitleFontsRevision++
        }.onFailure {
            subtitleFontImportFailed = true
        }
    }
    LaunchedEffect(subtitleFontImportRequest) {
        if (subtitleFontImportRequest > 0) importSubtitleFont()
    }
    LaunchedEffect(playerController, playerSettings.subtitleStyle, playerSettings.useLibass) {
        playerController?.applySubtitleStyle(playerSettings.subtitleStyle, playerSettings.useLibass)
    }
    LaunchedEffect(appFullscreen, pipAfterFullscreen) {
        if (pipAfterFullscreen && !appFullscreen) {
            kotlinx.coroutines.delay(1200)
            DesktopPlayerPictureInPicture.toggle()
            pipAfterFullscreen = false
        }
    }

    val nativeControlsState = PlayerControlsState(
        title = DeArrowDesktop.title(selected),
        subtitleLanguageItems = subtitleLanguages,
        subtitleOptionItems = subtitleOptions,
        nextEpisodeVisible = queueNext != null,
        nextEpisodeHeaderLabel = if (queueNext != null) "Next in playlist" else "Next video",
        nextEpisodeTitle = nextVideo?.title.orEmpty(),
        nextEpisodeThumbnail = nextVideo?.thumbnailUrl.orEmpty(),
        nextEpisodeActionLabel = "Play",
        nextEpisodePlayable = nextVideo != null,
        qualityOptions = if (isLive || qualityHeights.isEmpty()) emptyList() else listOf("Auto") + qualityHeights.map { "${it}p" },
        qualityLabel = qualityLabel,
        pipLabel = "Picture in picture",
        pipPlaceholderTitle = "Playing in picture in picture",
        pipRestoreLabel = "Back to the player",
        pipWindowTitle = DeArrowDesktop.title(selected),
        streamInfoLabel = "Stream info",
        streamInfoSections = youTubeStreamInfo(activePlayback, selected.videoId, qualityLabel),
        chapters = videoChapters.map { PlayerControlChapter(startMs = it.startMs, title = it.title) },
        subtitleStyle = playerSettings.subtitleStyle,
        subtitleDelayMs = subtitleDelayMs,
        subtitleFonts = subtitleFontItems,
        subtitleFontImportFailed = subtitleFontImportFailed,
        playbackSpeedLabel = formatPlaybackSpeedLabel(playbackSpeed),
    )

    LaunchedEffect(selectedSubtitle, playerController, activePlayback, YouTubeSettingsStore.translateCaptions) {
        val controller = playerController ?: return@LaunchedEffect
        val subtitle = selectedSubtitle
        // Loading a subtitle file can take a while (YouTube throttles it), so it runs off the screen thread;
        // otherwise the whole window stops responding until the file arrives.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (subtitle == null) {
                controller.clearExternalSubtitle()
            } else {
                controller.setSubtitleUri(subtitle.withTranslation().url)
            }
        }
    }

    // If the video ended before metadata (and therefore "up next") arrived,
    // advance as soon as a next item becomes available.
    LaunchedEffect(playbackEnded, autoplay, nextItem?.videoId) {
        if (playbackEnded && autoplay) {
            nextItem?.let(onOpen)
        }
    }

    // The player surface, drawn in the page or (window fullscreen) over the whole window.
    val playerSurface: @Composable (Modifier, Boolean) -> Unit = { surfaceModifier, fullscreen ->
        // Captured once per surface: the start position must not follow every progress update, or the player keeps re-seeking.
        val resumeAt = remember(activePlayback?.videoUrl, fullscreen) { playbackPosition.ms }
        // YouTube caps one long download at ~0.5 MB/s; the proxy fetches in 10 MB pieces instead.
        val source = remember(activePlayback) { activePlayback?.let(YouTubeStreamProxy::wrapPlayback) }
        LaunchedEffect(source?.videoUrl, fullscreen) {
            OneTubeDebugLog.line("surface mounted fullscreen=$fullscreen resumeAt=$resumeAt video=${source?.videoUrl?.take(40)}")
        }
        if (source != null) {
                        PlatformPlayerSurface(
                            sourceUrl = source.videoUrl,
                            sourceAudioUrl = source.audioUrl,
                            sourceHeaders = source.headers,
                            modifier = surfaceModifier,
                            initialPositionMs = resumeAt,
                            initialPositionRequestKey = "yt-resume-${selected.videoId}-$qualityRevision-$fullscreen",
                            useNativeController = true,
                            playerControlsState = nativeControlsState,
                            onPlayerControlsAction = { action ->
                                if (action == PlayerControlsAction.VideoSettings) {
                                    showQualityMenu = !showQualityMenu
                                    true
                                } else if (action == PlayerControlsAction.Back) {
                                    if (appFullscreen) toggleFullscreenAction()
                                    onBack()
                                    true
                                } else if (action == PlayerControlsAction.PictureInPicture) {
                                    if (appFullscreen) {
                                        pipAfterFullscreen = true
                                        toggleFullscreenAction()
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            },
                            onPlayerControlsEvent = { type, value ->
                                if (applySubtitleStyleControlEvent(
                                        type = type,
                                        value = value,
                                        onImportFont = {
                                            subtitleFontImportFailed = false
                                            subtitleFontImportRequest++
                                        },
                                    )
                                ) {
                                    if (type == "subtitleFontRemove") subtitleFontsRevision++
                                    true
                                } else if (type == "subtitleVisibility") {
                                    playerController?.setSubtitleVisible(value != 0.0)
                                    true
                                } else if (type == "subtitleDelayDelta") {
                                    subtitleDelayMs = (subtitleDelayMs + value.toInt())
                                        .coerceIn(-60_000, 60_000)
                                    playerController?.setSubtitleDelayMs(subtitleDelayMs)
                                    true
                                } else if (type == "subtitleDelayReset") {
                                    subtitleDelayMs = 0
                                    playerController?.setSubtitleDelayMs(0)
                                    true
                                } else if (type == "playNextEpisode") {
                                    if (queueNext != null) onQueueNext() else nextItem?.let(onOpen)
                                    true
                                } else if (type == "selectChapter") {
                                    videoChapters.getOrNull(value.toInt())?.let { chapter ->
                                        playerController?.seekTo(chapter.startMs)
                                    }
                                    true
                                } else if (type == "selectQuality") {
                                    val index = value.toInt()
                                    val height = if (index <= 0) null else qualityHeights.getOrNull(index - 1)
                                    // Only a real change reloads the video: the controls can report the current quality again
                                    // when the page refreshes, and that reload rebuilt the player mid-load.
                                    val alreadySelected = (index <= 0 && selectedQualityHeight == null) ||
                                        (index > 0 && height == selectedQualityHeight)
                                    if ((index == 0 || height != null) && !alreadySelected) {
                                        selectedQualityHeight = height
                                        changeQuality(height)
                                    }
                                    true
                                } else if (type == "selectAddonSubtitle" || type == "selectBuiltInSubtitleTrack") {
                                    val index = value.toInt()
                                    if (index < 0) {
                                        selectedSubtitle = null
                                        true
                                    } else {
                                        oneTubeVisibleSubtitles(activePlayback?.subtitles.orEmpty()).getOrNull(index)?.let { subtitle ->
                                            selectedSubtitle = subtitle
                                            true
                                        } ?: false
                                    }
                                } else {
                                    false
                                }
                            },
                            onControllerReady = { controller ->
                                playerController = controller
                                val native = controller.nativePlayer()
                                playbackPosition.nativeController = native
                                if (!music && native != null && source.audioTracks.size > 1) {
                                    val selectedTrack = selectPreferredAudioTrack(
                                        source.audioTracks,
                                        preferredAudioLanguage,
                                    ) ?: source.audioTracks.first()
                                    val baseAudioUrl = source.audioUrl
                                    val extraTracks = source.audioTracks.filter { it.url != baseAudioUrl }
                                    if (extraTracks.isNotEmpty()) {
                                        native.setAdditionalAudioTracks(
                                            extraTracks.map { track ->
                                                NativeAudioTrack(
                                                    url = track.url,
                                                    title = track.label,
                                                    language = track.language.orEmpty(),
                                                    isSelected = track.url == selectedTrack.url,
                                                )
                                            },
                                        )
                                    }
                                }
                                selectedSubtitle?.let { picked ->
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) { controller.setSubtitleUri(picked.url) }
                                }
                            },
                            onSnapshot = { snapshot ->
                                playbackSpeed = snapshot.playbackSpeed
                                if (snapshot.isPlaying != playbackPosition.lastPlaying || snapshot.isLoading != playbackPosition.lastLoading) {
                                    OneTubeDebugLog.line("state playing=${snapshot.isPlaying} loading=${snapshot.isLoading} pos=${snapshot.positionMs} dur=${snapshot.durationMs}")
                                    playbackPosition.lastPlaying = snapshot.isPlaying
                                    playbackPosition.lastLoading = snapshot.isLoading
                                }
                                val nowMs = System.currentTimeMillis()
                                if (nowMs - playbackPosition.lastTickMs >= 5000L) {
                                    playbackPosition.lastTickMs = nowMs
                                    OneTubeDebugLog.line("tick playing=${snapshot.isPlaying} loading=${snapshot.isLoading} pos=${snapshot.positionMs} proxyMB=${YouTubeStreamProxy.servedMb()} ${playbackPosition.nativeController?.debugStats().orEmpty()}")
                                }
                                if (kotlin.math.abs(snapshot.positionMs - playbackPosition.ms) > 3000L && playbackPosition.ms > 0L) {
                                    OneTubeDebugLog.line("jump from=${playbackPosition.ms} to=${snapshot.positionMs}")
                                }
                                // A stream that is still opening reports 0; keeping it would restart the video after a quality change.
                                if (snapshot.durationMs > 0L) playbackPosition.ms = snapshot.positionMs
                                if (snapshot.durationMs > 0L && playerDurationMs == 0L) playerDurationMs = snapshot.durationMs
                                if (skipSponsors && !snapshot.isLoading) {
                                    val position = snapshot.positionMs
                                    val segment = sponsorSegments.firstOrNull { candidate ->
                                        position >= candidate.startMs &&
                                            position < candidate.endMs &&
                                            candidate.action == SponsorSegment.ACTION_SKIP &&
                                            candidate.category != SponsorSegment.CATEGORY_POI_HIGHLIGHT
                                    }
                                    if (segment != null && segment.endMs - position > 750L) {
                                        if (YouTubeSettingsStore.sponsorAsk) {
                                            if (segment.startMs !in sponsorDeclined) sponsorPrompt = segment
                                        } else {
                                            playerController?.seekTo(segment.endMs)
                                            if (YouTubeSettingsStore.sponsorNotice) {
                                                sponsorNotice = "Skipped ${sponsorCategoryLabel(segment.category)}"
                                            }
                                        }
                                    } else if (sponsorPrompt != null) {
                                        sponsorPrompt?.let { sponsorDeclined += it.startMs }
                                        sponsorPrompt = null
                                    }
                                }
                                val position = snapshot.positionMs
                                if (!snapshot.isLoading && snapshot.durationMs > 0L && position > 0L) {
                                    val target = if (snapshot.isEnded) snapshot.durationMs else position
                                    val delta = target - lastSavedPositionMs
                                    if (snapshot.isEnded || delta >= 10_000L || delta <= -10_000L) {
                                        lastSavedPositionMs = target
                                        scope.launch {
                                            runCatching {
                                                YouTubeEngine.updateHistoryPosition(
                                                    selected.videoId,
                                                    target / 1000f,
                                                )
                                            }
                                        }
                                    }
                                }
                                val reachedEnd = snapshot.isEnded ||
                                    (!snapshot.isLoading && !isLive && snapshot.durationMs > 0L &&
                                        snapshot.positionMs >= snapshot.durationMs - 500L)
                                if (reachedEnd && !playbackEnded) {
                                    playbackEnded = true
                                    if (autoplay) {
                                        nextItem?.let(onOpen)
                                    }
                                }
                            },
                            onError = onPlaybackError,
                        )
        }
    }

    Box(modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    "OneTube",
                    style = MaterialTheme.typography.bodySmall,
                    color = YouTubeTextDim,
                )
            }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val viewportHeight = with(density) {
                LocalWindowInfo.current.containerSize.height.toDp()
            }
            // A Short is portrait: the player is a 9:16 frame at most of the window's height.
            val shortsLayout = selected.isShortClip() && !music
            val playerHeight = if (music) {
                220.dp
            } else if (shortsLayout) {
                viewportHeight * 0.82f
            } else {
                // Scale with the window, but leave room for the play bar and
                // metadata on very wide/high-resolution screens.
                minOf(maxWidth * 9f / 16f, viewportHeight * 0.56f)
            }
            Box(
                Modifier.fillMaxWidth().height(playerHeight)
                    .then(if (shortsLayout) Modifier else Modifier.background(Color.Black, RoundedCornerShape(10.dp))),
                contentAlignment = Alignment.Center,
            ) {
            val source = activePlayback
            when {
                resolving -> CircularProgressIndicator()
                source != null -> key(source.videoUrl, source.audioUrl) {
                    if (!appFullscreen) playerSurface(
                        if (shortsLayout) Modifier.width(playerHeight * 9f / 16f).fillMaxHeight().background(Color.Black) else Modifier.fillMaxSize(),
                        false,
                    )
                }
                playbackError != null -> Text(
                    playbackError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
                else -> CircularProgressIndicator()
            }
            // Shorts: Back on the left and Forward on the right of the centred frame, moving through the Shorts on screen.
            if (shortsLayout) {
                // Round green buttons either side of the frame, as on the Shorts apps.
                Box(
                    // The offset moves the button's top-left corner, so it's half the button's width past the frame edge.
                    Modifier.align(Alignment.Center).offset(x = -(playerHeight * 9f / 16f / 2 + 37.dp))
                        .size(46.dp).clip(CircleShape)
                        .background(Color(0xFF3CB043))
                        .clickable(enabled = queuePrevious != null, onClick = onQueuePrevious),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous short", tint = Color.White)
                }
                Box(
                    Modifier.align(Alignment.Center).offset(x = playerHeight * 9f / 16f / 2 + 37.dp)
                        .size(46.dp).clip(CircleShape)
                        .background(Color(0xFF3CB043))
                        .clickable(enabled = queueNext != null, onClick = onQueueNext),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next short", tint = Color.White)
                }
            }
            sponsorPrompt?.let { segment ->
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 86.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xE6101014))
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(sponsorCategoryLabel(segment.category).replaceFirstChar { it.uppercase() }, color = Color.White, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        playerController?.seekTo(segment.endMs)
                        sponsorDeclined += segment.startMs
                        sponsorPrompt = null
                    }) { Text("Skip") }
                    TextButton(onClick = {
                        sponsorDeclined += segment.startMs
                        sponsorPrompt = null
                    }) { Text("Watch", color = YouTubeTextMuted) }
                }
            }
            sponsorNotice?.let { notice ->
                Text(
                    notice,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xCC101014))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        }

        detailError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        // A Short shows only the video itself: no title, channel or details under it.
        if (!selected.isShortClip()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                DeArrowDesktop.title(selected),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                listOfNotNull(
                    details?.author ?: selected.author,
                    details?.subscriberCount,
                    details?.viewCount,
                    details?.publishedDate,
                ).joinToString(" \u00B7 "),
                color = Color.LightGray,
            )
            details?.likeCount?.let {
                Text("$it likes", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(YouTubePanel),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionPill(
                    icon = Icons.Filled.ThumbUp,
                    label = listOfNotNull("Like", details?.likeCount).joinToString(" "),
                    active = signedIn && details?.likeStatus == 1,
                    background = Color.Transparent,
                    onClick = onLike,
                )
                Box(Modifier.width(1.dp).height(22.dp).background(YouTubeBorder))
                ActionPill(
                    icon = Icons.Filled.ThumbDown,
                    label = "Dislike",
                    active = signedIn && details?.likeStatus == 2,
                    background = Color.Transparent,
                    onClick = onDislike,
                )
            }
            if (signedIn) {
                ActionPill(
                    icon = Icons.Filled.Add,
                    label = "Save",
                    background = Color.Transparent,
                    onClick = { saveMenuOpen = true },
                )
                if (saveMenuOpen) {
                    AlertDialog(
                        onDismissRequest = { saveMenuOpen = false },
                        title = { Text("Save to playlist") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                saveChoicesError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                                if (saveChoices == null && saveChoicesError == null) {
                                    CircularProgressIndicator(Modifier.size(22.dp))
                                }
                                saveChoices.orEmpty().forEach { choice ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = choice.containsVideo,
                                            onCheckedChange = { togglePlaylistMembership(choice) },
                                        )
                                        Text(
                                            choice.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = newPlaylistName,
                                    onValueChange = { newPlaylistName = it },
                                    singleLine = true,
                                    label = { Text("New playlist name") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                TextButton(
                                    enabled = newPlaylistName.isNotBlank(),
                                    onClick = {
                                        val name = newPlaylistName.trim()
                                        scope.launch {
                                            runCatching { YouTubeEngine.createPlaylist(name, selected.videoId) }
                                                .onSuccess {
                                                    newPlaylistName = ""
                                                    saveChoices = runCatching {
                                                        YouTubeEngine.playlistChoices(selected.videoId)
                                                    }.getOrNull() ?: saveChoices
                                                }
                                                .onFailure { saveChoicesError = it.message ?: "Could not create this playlist." }
                                        }
                                    },
                                ) { Text("Create and add") }
                            }
                        },
                        confirmButton = { TextButton(onClick = { saveMenuOpen = false }) { Text("Done") } },
                    )
                }
            }
            ActionPill(
                icon = Icons.Filled.Share,
                label = "Share",
                onClick = {
                    clipboard.setText(AnnotatedString("https://youtu.be/${selected.videoId}"))
                },
            )
            FilterChip(
                selected = showQualityMenu,
                onClick = { showQualityMenu = !showQualityMenu },
                label = {
                    Text("Quality: ${selectedQualityHeight?.let { "${it}p" } ?: "Auto"}")
                },
            )
            FilterChip(
                selected = skipSponsors,
                onClick = {
                    onSkipSponsorsChange(!skipSponsors)
                },
                label = { Text("Skip sponsors") },
            )
            FilterChip(
                selected = DeArrowDesktop.enabled,
                onClick = { DeArrowDesktop.enabled = !DeArrowDesktop.enabled },
                label = { Text("DeArrow") },
            )
        }

        val channelId = details?.channelId
        val channelName = details?.author ?: selected.author.orEmpty()
        val channelAvatarUrl = details?.authorImageUrl
        val subscriberText = details?.subscriberCount.orEmpty()
        // Subscription and like state belong to a signed-in account: when signed out, the page shows neither.
        val channelSubscribed = signedIn && details?.isSubscribed == true
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(YouTubePanel)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(YouTubePanelRaised)
                    .clickable(enabled = channelId != null) {
                        channelId?.let { onOpenChannel(it, channelName) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (!channelAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channelAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        channelName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD5D5DF),
                    )
                }
            }
            Column(
                Modifier.weight(1f).clickable(enabled = channelId != null) {
                    channelId?.let { onOpenChannel(it, channelName) }
                },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        channelName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (channelSubscribed) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = YouTubeTextDim,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Text(
                    subscriberText.ifBlank { "YouTube channel" },
                    style = MaterialTheme.typography.bodySmall,
                    color = YouTubeTextDim,
                )
            }
            Button(
                onClick = onSubscribe,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (channelSubscribed) Color(0xFF2A2A34) else YouTubeRed,
                    contentColor = Color.White,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            ) {
                Text(if (channelSubscribed) "Subscribed" else "Subscribe")
            }
            if (details?.notificationStates.orEmpty().isNotEmpty()) {
                Box {
                    IconButton(onClick = { notificationsMenuOpen = true }) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = "Notification settings",
                            tint = YouTubeTextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = notificationsMenuOpen,
                        onDismissRequest = { notificationsMenuOpen = false },
                    ) {
                        details?.notificationStates.orEmpty().forEach { state ->
                            DropdownMenuItem(
                                text = { Text(state.title ?: "Notifications") },
                                trailingIcon = if (state.isSelected) {
                                    {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    notificationsMenuOpen = false
                                    onNotificationState(state)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showQualityMenu) {
            val heights = activePlayback?.availableVideoHeights.orEmpty()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = selectedQualityHeight == null,
                    onClick = {
                        selectedQualityHeight = null
                        changeQuality(null)
                    },
                    label = { Text("Auto") },
                )
                heights.forEach { height ->
                    FilterChip(
                        selected = selectedQualityHeight == height,
                        onClick = {
                            selectedQualityHeight = height
                            changeQuality(height)
                        },
                        label = { Text("${height}p") },
                    )
                }
                if (qualityLoading) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp))
            }
            qualityError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        actionMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0xFF9AE6B4), style = MaterialTheme.typography.bodySmall)
        }

        details?.description?.takeIf { it.isNotBlank() }?.let { description ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(YouTubePanel)
                    .clickable { descriptionExpanded = !descriptionExpanded }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD0D0D8),
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (descriptionExpanded) "Show less" else "\u2026more",
                    style = MaterialTheme.typography.labelSmall,
                    color = YouTubeTextMuted,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (chatMessages.isNotEmpty() && liveChatEnabled) {
            HorizontalDivider(color = YouTubeBorder)
            Text("Live chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            chatMessages.takeLast(40).forEach { message ->
                val author = message.authorName.orEmpty()
                val alpha = YouTubeSettingsStore.liveChatOpacityPercent / 100f
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = chatNameColor(author).copy(alpha = alpha), fontWeight = FontWeight.SemiBold)) {
                            append(author)
                        }
                        withStyle(SpanStyle(color = Color(0xFFD0D0D8).copy(alpha = alpha))) {
                            append(": ${message.message.orEmpty()}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = YouTubeSettingsStore.liveChatFontSp.sp,
                )
            }
        }

        HorizontalDivider(color = YouTubeBorder)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Comments${if (comments.isNotEmpty()) " (${comments.size})" else ""}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (commentsLoading) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp))
            Spacer(Modifier.weight(1f))
            if (canSortComments) {
                FilterChip(
                    selected = !commentsNewest,
                    onClick = { onCommentSortChange(false) },
                    label = { Text("Top") },
                )
                FilterChip(
                    selected = commentsNewest,
                    onClick = { onCommentSortChange(true) },
                    label = { Text("Newest") },
                )
            }
        }
        commentsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (comments.isEmpty() && !commentsLoading && commentsError == null) {
            Text("No comments yet.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }

        comments.forEach { comment ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(
                    model = comment.authorPhoto,
                    contentDescription = null,
                    modifier = Modifier.width(32.dp).height(32.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(comment.author, comment.publishedDate).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.LightGray,
                    )
                    Text(
                        comment.message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE0E0E8),
                    )
                    if (comment.likeActionKey != null) {
                        TextButton(onClick = { onToggleCommentLike(comment) }) {
                            Text(
                                if (comment.liked) "Liked" else "Like",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    if (comment.dislikeActionKey != null) {
                        TextButton(onClick = { onToggleCommentDislike(comment) }) {
                            Text("Dislike", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    comment.likeCount?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    comment.replyKey?.let { key ->
                        val replies = repliesByKey[key].orEmpty()
                        val loadingReplies = key in repliesLoading
                        if (replies.isEmpty()) {
                            TextButton(onClick = { loadReplies(comment) }, enabled = !loadingReplies) {
                                Text(
                                    if (loadingReplies) "Loading replies�" else {
                                        val count = comment.replyCount?.takeIf { it.isNotBlank() }
                                        if (count != null) "View $count replies" else "View replies"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        } else {
                            replies.forEach { reply ->
                                Column(
                                    Modifier.padding(start = 14.dp, top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        listOfNotNull(reply.author, reply.publishedDate).joinToString(" � "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.LightGray,
                                    )
                                    Text(
                                        reply.message.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE0E0E8),
                                    )
                                    if (reply.likeActionKey != null) {
                                        TextButton(onClick = { onToggleCommentLike(reply) }) {
                                            Text(
                                                if (reply.liked) "Liked" else "Like",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                    if (reply.dislikeActionKey != null) {
                                        TextButton(onClick = { onToggleCommentDislike(reply) }) {
                                            Text("Dislike", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (nextCommentsKey != null) {
            TextButton(onClick = onLoadMoreComments, enabled = !commentsLoading) {
                Text("Load more comments")
            }
        }

        }

        // A Short is just the Short: no autoplay, up-next or queue panel beside it.
        if (!music && !selected.isShortClip()) {
            WatchSidebar(
                modifier = Modifier.width(342.dp).fillMaxHeight(),
                suggestions = details?.suggestions.orEmpty(),
                autoplay = autoplay,
                onAutoplayChange = onAutoplayChange,
                onOpen = onOpen,
                queueNext = queueNext,
                onQueueNext = onQueueNext,
            )
        }
    }
    // Window full screen: the player covers the window, with the same single player (as in Music).
    if (appFullscreen && activePlayback != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            key(activePlayback.videoUrl, activePlayback.audioUrl) {
                playerSurface(Modifier.fillMaxSize(), true)
            }
        }
    }
    }
}



@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    background: Color = YouTubePanel,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0x22FF1F45) else background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) YouTubeRed else Color(0xFFD0D0D8),
            modifier = Modifier.size(17.dp),
        )
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (active) YouTubeRed else Color(0xFFD0D0D8),
            )
        }
    }
}

@Composable
private fun WatchSidebar(
    modifier: Modifier,
    suggestions: List<YouTubeVideoItem>,
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    onOpen: (YouTubeVideoItem) -> Unit,
    queueNext: YouTubeVideoItem?,
    onQueueNext: () -> Unit,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(top = 18.dp, end = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(YouTubePanel)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Autoplay next",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (autoplay) "On \u00B7 suggestions from the engine" else "Off",
                    style = MaterialTheme.typography.labelSmall,
                    color = YouTubeTextDim,
                )
            }
            Switch(checked = autoplay, onCheckedChange = onAutoplayChange)
        }

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(YouTubePanel)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Up next",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (suggestions.isEmpty()) {
                Text(
                    "Suggestions will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                )
            } else {
                suggestions.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpen(item) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.width(144.dp).aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(YouTubePanelRaised),
                        ) {
                            YouTubeThumbnail(
                                item = item,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (item.isLive || item.durationSeconds > 0) {
                                Text(
                                    if (item.isLive) "LIVE" else formatDuration(item.durationSeconds),
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                )
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                DeArrowDesktop.title(item),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.author.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = YouTubeTextDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(YouTubePanel)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Queue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (queueNext != null) "1 video" else "Empty",
                    style = MaterialTheme.typography.labelSmall,
                    color = YouTubeTextDim,
                )
            }
            if (queueNext != null) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onQueueNext)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.width(132.dp).aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(YouTubePanelRaised),
                    ) {
                        YouTubeThumbnail(
                            item = queueNext,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (queueNext.isLive || queueNext.durationSeconds > 0) {
                            Text(
                                if (queueNext.isLive) "LIVE" else formatDuration(queueNext.durationSeconds),
                                modifier = Modifier.align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            DeArrowDesktop.title(queueNext),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            queueNext.author.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = YouTubeTextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    "Queue is empty.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                )
            }
        }
    }
}

@Composable
private fun SearchFilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(label) },
    )
}

private val searchTypeOptions = listOf(
    "All" to 0,
    "Videos" to SearchOptions.TYPE_VIDEO,
    "Channels" to SearchOptions.TYPE_CHANNEL,
    "Playlists" to SearchOptions.TYPE_PLAYLIST,
    "Movies" to SearchOptions.TYPE_MOVIE,
)

private val searchSortOptions = listOf(
    "Relevance" to SearchOptions.SORT_BY_RELEVANCE,
    "Newest" to SearchOptions.SORT_BY_UPLOAD_DATE,
    "Views" to SearchOptions.SORT_BY_VIEW_COUNT,
    "Rating" to SearchOptions.SORT_BY_RATING,
)

private val searchDateOptions = listOf(
    "Any time" to 0,
    "Last hour" to SearchOptions.UPLOAD_DATE_LAST_HOUR,
    "Today" to SearchOptions.UPLOAD_DATE_TODAY,
    "This week" to SearchOptions.UPLOAD_DATE_THIS_WEEK,
    "This month" to SearchOptions.UPLOAD_DATE_THIS_MONTH,
    "This year" to SearchOptions.UPLOAD_DATE_THIS_YEAR,
)

private val searchDurationOptions = listOf(
    "Any" to 0,
    "Under 4 min" to SearchOptions.DURATION_UNDER_4,
    "4-20 min" to SearchOptions.DURATION_BETWEEN_4_20,
    "Over 20 min" to SearchOptions.DURATION_OVER_20,
)

/**
 * The desktop player behind [this]. The surface hands out a wrapper (subtitle auto-sync) that hides
 * it, so a plain cast silently fails and storyboards, extra audio tracks and player stats are skipped.
 */
private fun PlayerEngineController.nativePlayer(): NativePlayerController? =
    this as? NativePlayerController
        ?: (this as? com.nuvio.app.features.autosync.AutoSyncMpvEngineController)?.wrapped as? NativePlayerController

/** "Stream info" in the player: the format and size of the video and audio streams, read from their URLs. */
private fun youTubeStreamInfo(playback: YouTubePlayback?, videoId: String, quality: String): List<PlayerControlInfoSection> {
    if (playback == null) return emptyList()
    fun params(url: String?): Map<String, String> {
        val query = url?.let { runCatching { java.net.URI(it).rawQuery }.getOrNull() }.orEmpty()
        return query.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to runCatching { java.net.URLDecoder.decode(part.substring(i + 1), "UTF-8") }.getOrDefault("")
        }.toMap()
    }
    fun describe(title: String, url: String?): PlayerControlInfoSection? {
        if (url.isNullOrBlank()) return null
        val q = params(url)
        val mime = q["mime"].orEmpty()
        val items = buildList {
            if (mime.isNotBlank()) add(PlayerControlInfoItem("Format", mime))
            q["clen"]?.toLongOrNull()?.takeIf { it > 0 }?.let { add(PlayerControlInfoItem("Size", "%.1f MB".format(it / 1_048_576.0))) }
            q["itag"]?.let { add(PlayerControlInfoItem("itag", it)) }
            if (url.contains(".m3u8") || url.contains("/manifest/")) add(PlayerControlInfoItem("Type", "HLS (live)"))
        }
        return PlayerControlInfoSection(title = title, items = items)
    }
    return listOfNotNull(
        PlayerControlInfoSection(
            title = "YouTube",
            items = listOf(
                PlayerControlInfoItem("Video ID", videoId),
                PlayerControlInfoItem("Quality", quality),
                PlayerControlInfoItem("Available", playback.availableVideoHeights.distinct().sortedDescending().joinToString(", ") { "${it}p" }.ifBlank { "-" }),
            ),
        ),
        describe("Video", playback.videoUrl),
        describe("Audio", playback.audioUrl),
    )
}

private val youtubeQualityOptions = listOf("Auto", "720p", "1080p", "1440p", "4K")
private val youtubeAudioOptions = listOf(
    "Original",
    "English",
    "Spanish",
    "French",
    "German",
    "Japanese",
    "Korean",
    "Portuguese",
)

@Composable
private fun YouTubeSettingsDialog(
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    skipSponsors: Boolean,
    onSkipSponsorsChange: (Boolean) -> Unit,
    dearrow: Boolean,
    onDeArrowChange: (Boolean) -> Unit,
    resumePlayback: Boolean,
    onResumePlaybackChange: (Boolean) -> Unit,
    preferredQuality: String,
    onPreferredQualityChange: (String) -> Unit,
    audioLanguage: String,
    onAudioLanguageChange: (String) -> Unit,
    storyboardPreviews: Boolean,
    onStoryboardPreviewsChange: (Boolean) -> Unit,
    liveChat: Boolean,
    onLiveChatChange: (Boolean) -> Unit,
    signedIn: Boolean,
    accountName: String?,
    onSwitchAccount: () -> Unit,
    onSignIn: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(840.dp).heightIn(max = 640.dp),
            shape = RoundedCornerShape(22.dp),
            color = YouTubePanelRaised,
            contentColor = Color.White,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "OneTube settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Playback, enhancements and account",
                            style = MaterialTheme.typography.labelSmall,
                            color = YouTubeTextDim,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                HorizontalDivider(color = YouTubeBorder)
                YouTubeSettingsBody(
                    autoplay = autoplay,
                    onAutoplayChange = onAutoplayChange,
                    skipSponsors = skipSponsors,
                    onSkipSponsorsChange = onSkipSponsorsChange,
                    dearrow = dearrow,
                    onDeArrowChange = onDeArrowChange,
                    resumePlayback = resumePlayback,
                    onResumePlaybackChange = onResumePlaybackChange,
                    preferredQuality = preferredQuality,
                    onPreferredQualityChange = onPreferredQualityChange,
                    audioLanguage = audioLanguage,
                    onAudioLanguageChange = onAudioLanguageChange,
                    storyboardPreviews = storyboardPreviews,
                    onStoryboardPreviewsChange = onStoryboardPreviewsChange,
                    liveChat = liveChat,
                    onLiveChatChange = onLiveChatChange,
                    signedIn = signedIn,
                    accountName = accountName,
                    onSwitchAccount = onSwitchAccount,
                    onSignIn = onSignIn,
                    onReset = onReset,
                    showAccount = true,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                )
            }
        }
    }
}

/**
 * OneTube settings for Settings → Sections → OneTube: the same options as the gear menu in
 * OneTube, read from and written to [YouTubeSettingsStore]. Signing in stays in OneTube itself.
 */
@Composable
internal fun OneTubeSettingsPage(modifier: Modifier = Modifier) {
    var autoplay by remember { mutableStateOf(YouTubeSettingsStore.autoplay) }
    var skipSponsors by remember { mutableStateOf(YouTubeSettingsStore.sponsorSkip) }
    var resumePlayback by remember { mutableStateOf(YouTubeSettingsStore.resumePlayback) }
    var preferredQuality by remember { mutableStateOf(YouTubeSettingsStore.preferredQuality) }
    var audioLanguage by remember { mutableStateOf(YouTubeSettingsStore.preferredAudioLanguage) }
    var storyboardPreviews by remember { mutableStateOf(YouTubeSettingsStore.storyboardPreviews) }
    var liveChat by remember { mutableStateOf(YouTubeSettingsStore.liveChat) }
    YouTubeSettingsBody(
        autoplay = autoplay,
        onAutoplayChange = { autoplay = it; YouTubeSettingsStore.autoplay = it },
        skipSponsors = skipSponsors,
        onSkipSponsorsChange = { skipSponsors = it; YouTubeSettingsStore.sponsorSkip = it },
        dearrow = DeArrowDesktop.enabled,
        onDeArrowChange = { DeArrowDesktop.enabled = it },
        resumePlayback = resumePlayback,
        onResumePlaybackChange = { resumePlayback = it; YouTubeSettingsStore.resumePlayback = it },
        preferredQuality = preferredQuality,
        onPreferredQualityChange = { preferredQuality = it; YouTubeSettingsStore.preferredQuality = it },
        audioLanguage = audioLanguage,
        onAudioLanguageChange = { audioLanguage = it; YouTubeSettingsStore.preferredAudioLanguage = it },
        storyboardPreviews = storyboardPreviews,
        onStoryboardPreviewsChange = { storyboardPreviews = it; YouTubeSettingsStore.storyboardPreviews = it },
        liveChat = liveChat,
        onLiveChatChange = { liveChat = it; YouTubeSettingsStore.liveChat = it },
        signedIn = false,
        accountName = null,
        onSwitchAccount = {},
        onSignIn = {},
        onReset = {
            YouTubeSettingsStore.reset()
            DeArrowDesktop.enabled = true
            autoplay = YouTubeSettingsStore.autoplay
            skipSponsors = YouTubeSettingsStore.sponsorSkip
            resumePlayback = YouTubeSettingsStore.resumePlayback
            preferredQuality = YouTubeSettingsStore.preferredQuality
            audioLanguage = YouTubeSettingsStore.preferredAudioLanguage
            storyboardPreviews = YouTubeSettingsStore.storyboardPreviews
            liveChat = YouTubeSettingsStore.liveChat
        },
        showAccount = false,
        modifier = modifier,
    )
}

/** The OneTube options themselves (two scrolling columns), shared by the gear dialog and the Settings page. */
@Composable
private fun YouTubeSettingsBody(
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    skipSponsors: Boolean,
    onSkipSponsorsChange: (Boolean) -> Unit,
    dearrow: Boolean,
    onDeArrowChange: (Boolean) -> Unit,
    resumePlayback: Boolean,
    onResumePlaybackChange: (Boolean) -> Unit,
    preferredQuality: String,
    onPreferredQualityChange: (String) -> Unit,
    audioLanguage: String,
    onAudioLanguageChange: (String) -> Unit,
    storyboardPreviews: Boolean,
    onStoryboardPreviewsChange: (Boolean) -> Unit,
    liveChat: Boolean,
    onLiveChatChange: (Boolean) -> Unit,
    signedIn: Boolean,
    accountName: String?,
    onSwitchAccount: () -> Unit,
    onSignIn: () -> Unit,
    onReset: () -> Unit,
    showAccount: Boolean,
    modifier: Modifier,
) {
                Row(
                    modifier,
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SettingsSectionLabel("Playback")
                        SettingsToggle(
                            title = "Autoplay next video",
                            description = "Continue with the next item when playback ends.",
                            checked = autoplay,
                            onCheckedChange = onAutoplayChange,
                        )
                        SettingsToggle(
                            title = "Resume where you left off",
                            description = "Start from your saved position.",
                            checked = resumePlayback,
                            onCheckedChange = onResumePlaybackChange,
                        )
                        SettingsChoiceRow(
                            title = "Preferred quality",
                            options = youtubeQualityOptions,
                            selected = preferredQuality,
                            onSelect = onPreferredQualityChange,
                        )
                        SettingsChoiceRow(
                            title = "Audio language",
                            options = youtubeAudioOptions,
                            selected = audioLanguage,
                            onSelect = onAudioLanguageChange,
                        )
                        SettingsToggle(
                            title = "Automatically select subtitles",
                            description = "Start videos with subtitles in your language when they have them.",
                            checked = YouTubeSettingsStore.autoSubtitles,
                            onCheckedChange = { YouTubeSettingsStore.autoSubtitles = it },
                        )
                        if (YouTubeSettingsStore.autoSubtitles) {
                            SettingsChoiceRow(
                                title = "Subtitle language",
                                options = oneTubeSubtitleLanguages.map { it.second },
                                selected = oneTubeSubtitleLanguages.firstOrNull { it.first == YouTubeSettingsStore.subtitleLanguage }?.second ?: "English",
                                onSelect = { label ->
                                    oneTubeSubtitleLanguages.firstOrNull { it.second == label }?.let { YouTubeSettingsStore.subtitleLanguage = it.first }
                                },
                            )
                        }
                        SettingsChoiceRow(
                            title = "Translate captions",
                            options = listOf("Off") + oneTubeSubtitleLanguages.map { it.second },
                            selected = oneTubeSubtitleLanguages.firstOrNull { it.first == YouTubeSettingsStore.translateCaptions }?.second ?: "Off",
                            onSelect = { label ->
                                YouTubeSettingsStore.translateCaptions =
                                    oneTubeSubtitleLanguages.firstOrNull { it.second == label }?.first.orEmpty()
                            },
                        )
                        Text(
                            "Only tracks YouTube marks as translatable can be translated.",
                            style = MaterialTheme.typography.bodySmall,
                            color = YouTubeTextMuted,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )

                        // Caption size and weight are the player's own subtitle style, so they apply to every video.
                        SettingsSectionLabel("Captions")
                        val captionStyle = com.nuvio.app.features.player.PlayerSettingsRepository.uiState.collectAsState().value.subtitleStyle
                        SettingsChoiceRow(
                            title = "Caption size",
                            options = listOf("Small", "Medium", "Large"),
                            selected = when {
                                captionStyle.fontSizeSp <= 16 -> "Small"
                                captionStyle.fontSizeSp <= 20 -> "Medium"
                                else -> "Large"
                            },
                            onSelect = { label ->
                                val size = when (label) {
                                    "Small" -> 16
                                    "Large" -> 24
                                    else -> 18
                                }
                                com.nuvio.app.features.player.PlayerSettingsRepository.setSubtitleStyle(captionStyle.copy(fontSizeSp = size))
                            },
                        )
                        SettingsToggle(
                            title = "Bold captions",
                            description = "Heavier text for captions in every player.",
                            checked = captionStyle.bold,
                            onCheckedChange = {
                                com.nuvio.app.features.player.PlayerSettingsRepository.setSubtitleStyle(captionStyle.copy(bold = it))
                            },
                        )

                        SettingsSectionLabel("Layout")
                        SettingsChoiceRow(
                            title = "Posters per row",
                            options = listOf("Auto", "3", "4", "5", "6"),
                            selected = YouTubeSettingsStore.postersPerRow.takeIf { it > 0 }?.toString() ?: "Auto",
                            onSelect = { YouTubeSettingsStore.postersPerRow = it.toIntOrNull() ?: 0 },
                        )
                        SettingsToggle(
                            title = "Clock",
                            description = "Show the time in the OneTube header.",
                            checked = YouTubeSettingsStore.headerClock,
                            onCheckedChange = { YouTubeSettingsStore.headerClock = it },
                        )

                        SettingsSectionLabel("Tabs")
                        OneTubeTabsEditor()
                        SettingsToggle(
                            title = "Open on the last tab used",
                            description = "Off: OneTube always opens on the first tab.",
                            checked = YouTubeSettingsStore.openOnLastTab,
                            onCheckedChange = { YouTubeSettingsStore.openOnLastTab = it },
                        )
                        Text(
                            "Nuvio accent colour and density are controlled in the main Settings section.",
                            style = MaterialTheme.typography.labelSmall,
                            color = YouTubeTextDim,
                        )
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SettingsSectionLabel("Enhancements")
                        SettingsToggle(
                            title = "SponsorBlock",
                            description = "Skip parts of videos viewers have marked, like sponsor reads.",
                            checked = skipSponsors,
                            onCheckedChange = onSkipSponsorsChange,
                        )
                        if (skipSponsors) {
                            SettingsToggle(
                                title = "Ask before skipping",
                                description = "Show a Skip button when a marked part starts instead of skipping it.",
                                checked = YouTubeSettingsStore.sponsorAsk,
                                onCheckedChange = { YouTubeSettingsStore.sponsorAsk = it },
                            )
                            if (!YouTubeSettingsStore.sponsorAsk) {
                                SettingsToggle(
                                    title = "Show a notice when skipping",
                                    description = "A short message such as \"Skipped sponsor\".",
                                    checked = YouTubeSettingsStore.sponsorNotice,
                                    onCheckedChange = { YouTubeSettingsStore.sponsorNotice = it },
                                )
                            }
                        }
                        SettingsToggle(
                            title = "DeArrow",
                            description = "Community-written titles and thumbnails in place of clickbait.",
                            checked = dearrow,
                            onCheckedChange = onDeArrowChange,
                        )
                        if (dearrow) {
                            SettingsToggle(
                                title = "Better titles",
                                description = "Plain titles where the community has written one.",
                                checked = DeArrowDesktop.titles,
                                onCheckedChange = { DeArrowDesktop.titles = it },
                            )
                            SettingsToggle(
                                title = "Better thumbnails",
                                description = "Thumbnails picked from the video itself.",
                                checked = DeArrowDesktop.thumbnails,
                                onCheckedChange = { DeArrowDesktop.thumbnails = it },
                            )
                        }
                        SettingsToggle(
                            title = "Storyboard previews",
                            description = "Show thumbnail previews on the player seek bar.",
                            checked = storyboardPreviews,
                            onCheckedChange = onStoryboardPreviewsChange,
                        )
                        SettingsToggle(
                            title = "Live chat",
                            description = "Load the chat panel for live streams.",
                            checked = liveChat,
                            onCheckedChange = onLiveChatChange,
                        )
                        if (liveChat) {
                            SettingsChoiceRow(
                                title = "Chat text size",
                                options = listOf("Small", "Medium", "Large"),
                                selected = when (YouTubeSettingsStore.liveChatFontSp) {
                                    11 -> "Small"
                                    16 -> "Large"
                                    else -> "Medium"
                                },
                                onSelect = {
                                    YouTubeSettingsStore.liveChatFontSp = when (it) {
                                        "Small" -> 11
                                        "Large" -> 16
                                        else -> 13
                                    }
                                },
                            )
                            SettingsChoiceRow(
                                title = "Chat opacity",
                                options = listOf("50%", "75%", "100%"),
                                selected = "${YouTubeSettingsStore.liveChatOpacityPercent}%",
                                onSelect = { YouTubeSettingsStore.liveChatOpacityPercent = it.removeSuffix("%").toIntOrNull() ?: 100 },
                            )
                        }

                        SettingsSectionLabel("Feeds")
                        Text(
                            "Leave things out of the OneTube page. History always shows everything you've watched.",
                            style = MaterialTheme.typography.labelSmall,
                            color = YouTubeTextDim,
                        )
                        SettingsToggle(
                            title = "Hide Shorts",
                            description = "Short vertical videos (the Shorts tab still shows them).",
                            checked = YouTubeSettingsStore.hideShorts,
                            onCheckedChange = { YouTubeSettingsStore.hideShorts = it },
                        )
                        SettingsToggle(
                            title = "Hide watched videos",
                            description = "Videos you've mostly watched.",
                            checked = YouTubeSettingsStore.hideWatched,
                            onCheckedChange = { YouTubeSettingsStore.hideWatched = it },
                        )
                        SettingsToggle(
                            title = "Hide upcoming videos",
                            description = "Premieres and streams that haven't started yet.",
                            checked = YouTubeSettingsStore.hideUpcoming,
                            onCheckedChange = { YouTubeSettingsStore.hideUpcoming = it },
                        )
                        OneTubeHiddenListEditor(
                            title = "Hidden words",
                            hint = "Add a word or phrase",
                            values = YouTubeSettingsStore.hiddenWords,
                            onChange = { YouTubeSettingsStore.hiddenWords = it },
                        )
                        OneTubeHiddenListEditor(
                            title = "Hidden channels",
                            hint = "Add a channel name",
                            values = YouTubeSettingsStore.hiddenChannels,
                            onChange = { YouTubeSettingsStore.hiddenChannels = it },
                        )

                        if (showAccount) SettingsSectionLabel("Account")
                        if (showAccount) Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (signedIn) accountName?.takeIf { it.isNotBlank() } ?: "Signed in" else "Not signed in",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (signedIn) "YouTube account" else "Sign in to unlock Home and subscriptions.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = YouTubeTextDim,
                                )
                            }
                            TextButton(onClick = if (signedIn) onSwitchAccount else onSignIn) {
                                Text(if (signedIn) "Switch account" else "Sign in")
                            }
                        }

                        SettingsSectionLabel("Reset")
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Reset YouTube playback settings",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = YouTubeTextMuted,
                            )
                            TextButton(onClick = onReset) { Text("Reset") }
                        }
                    }
                }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = YouTubeTextDim,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = YouTubeTextDim)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}









/** The OneTube tabs in the user's order (Settings → Tabs); new tabs keep their default place. */
private fun orderedOneTubeTabs(order: List<String>): List<YouTubeTab> {
    val defaults = listOf(
        YouTubeTab.HOME,
        YouTubeTab.MUSIC,
        YouTubeTab.GAMING,
        YouTubeTab.NEWS,
        YouTubeTab.SPORTS,
        YouTubeTab.LIVE,
        YouTubeTab.SUBSCRIPTIONS,
        YouTubeTab.HISTORY,
        YouTubeTab.PLAYLISTS,
        YouTubeTab.SHORTS,
        YouTubeTab.CHANNELS,
    )
    val ordered = order.mapNotNull { name -> defaults.firstOrNull { it.name == name } }
    return ordered + defaults.filter { it !in ordered }
}

/** Grid columns: the user's "Posters per row" when set, otherwise fit the window. */
private fun oneTubeGridCells(minWidth: androidx.compose.ui.unit.Dp): GridCells {
    val perRow = YouTubeSettingsStore.postersPerRow
    return if (perRow > 0) GridCells.Fixed(perRow) else GridCells.Adaptive(minWidth)
}


/** Readable name for a SponsorBlock category ("sponsor", "selfpromo", …). */
private fun sponsorCategoryLabel(category: String?): String = when (category) {
    SponsorSegment.CATEGORY_SPONSOR -> "sponsor"
    SponsorSegment.CATEGORY_SELF_PROMO -> "self-promotion"
    SponsorSegment.CATEGORY_INTRO -> "intro"
    SponsorSegment.CATEGORY_OUTRO -> "outro"
    SponsorSegment.CATEGORY_INTERACTION -> "subscribe reminder"
    SponsorSegment.CATEGORY_PREVIEW_RECAP -> "recap"
    SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC -> "non-music part"
    SponsorSegment.CATEGORY_FILLER -> "filler"
    else -> "marked part"
}

/** The time in the OneTube header (Settings → Clock). */
@Composable
private fun OneTubeHeaderClock() {
    var now by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.time.LocalTime.now()
            delay(20_000L)
        }
    }
    Text(
        now.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)),
        style = MaterialTheme.typography.titleSmall,
        color = YouTubeTextMuted,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}


/**
 * The track to load in the player: when the viewer picked a caption translation
 * language and YouTube marks this track as translatable, the timedtext URL gets `tlang`.
 */
private fun YouTubeSubtitle.withTranslation(): YouTubeSubtitle {
    val target = YouTubeSettingsStore.translateCaptions
    if (target.isBlank() || !translatable || language.lowercase().startsWith(target.lowercase())) return this
    return copy(url = "$url&tlang=$target", language = target, name = name, isAutoGenerated = false)
}

private val oneTubeSubtitleLanguages = listOf(
    "en" to "English",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "pt" to "Portuguese",
    "it" to "Italian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "hi" to "Hindi",
    "ar" to "Arabic",
)

/** Show/hide and reorder the OneTube tabs. */
@Composable
private fun OneTubeTabsEditor() {
    val order = orderedOneTubeTabs(YouTubeSettingsStore.tabOrder)
    val hidden = YouTubeSettingsStore.hiddenTabs
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        order.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = entry.name !in hidden,
                    onCheckedChange = { show ->
                        YouTubeSettingsStore.hiddenTabs = if (show) hidden - entry.name else hidden + entry.name
                    },
                )
                Text(entry.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val list = order.map { it.name }.toMutableList()
                        java.util.Collections.swap(list, index, index - 1)
                        YouTubeSettingsStore.tabOrder = list
                    },
                    enabled = index > 0,
                    modifier = Modifier.size(30.dp),
                ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${entry.label} up", modifier = Modifier.size(18.dp)) }
                IconButton(
                    onClick = {
                        val list = order.map { it.name }.toMutableList()
                        java.util.Collections.swap(list, index, index + 1)
                        YouTubeSettingsStore.tabOrder = list
                    },
                    enabled = index < order.lastIndex,
                    modifier = Modifier.size(30.dp),
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${entry.label} down", modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

/** A list of hidden words or channels: add with the field, remove with ✕. */
@Composable
private fun OneTubeHiddenListEditor(
    title: String,
    hint: String,
    values: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(hint) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    if (draft.isNotBlank()) {
                        onChange(values + draft.trim())
                        draft = ""
                    }
                }),
            )
            TextButton(
                onClick = {
                    onChange(values + draft.trim())
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) { Text("Add") }
        }
        if (values.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                values.forEach { value ->
                    InputChip(
                        selected = false,
                        onClick = { onChange(values - value) },
                        label = { Text(value) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Stop hiding $value", modifier = Modifier.size(14.dp)) },
                    )
                }
            }
        }
    }
}

/** Playback position read by the watch page without redrawing it on every progress update. */
private class PlaybackPosition(var ms: Long) {
    var lastPlaying: Boolean? = null
    var lastLoading: Boolean? = null
    var lastTickMs: Long = 0L
    var nativeController: NativePlayerController? = null
}
