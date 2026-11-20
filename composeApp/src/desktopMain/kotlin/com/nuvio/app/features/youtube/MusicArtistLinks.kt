package com.nuvio.app.features.youtube

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Opens an artist page from anywhere in Music, including the Now Playing
 * overlay. A missing channel id is resolved by name on the Music screen.
 */
internal fun openMusicArtist(name: String, channelId: String?) {
    val clean = name.trim()
    if (clean.isBlank()) return
    MusicNavigation.artistRequest = MusicArtistRequest(channelId?.takeIf { it.isNotBlank() }, clean)
    MusicPlaybackQueue.expanded = false
}

/** The credited artists for [item]: per-artist ids when looked up, else the split byline. */
internal fun musicArtistsFor(item: YouTubeVideoItem): List<MusicTrackArtist> {
    MusicTrackInfoStore.info[item.videoId]?.artists?.takeIf { it.isNotEmpty() }?.let { return it }
    val names = splitArtistNames(item.author)
    if (names.isEmpty()) return emptyList()
    // Only a single credit can safely use the item's own channel.
    return if (names.size == 1) listOf(MusicTrackArtist(names[0], item.channelId)) else names.map { MusicTrackArtist(it, null) }
}

/**
 * An artist byline where every name is its own link to that artist's page.
 * Non-track rows (playlists, albums, channels) show their subtitle as plain text.
 */
@Composable
internal fun MusicArtistLinks(
    item: YouTubeVideoItem,
    fontSize: TextUnit = 13.sp,
    color: Color = Color(0xFFB7BCC6),
    modifier: Modifier = Modifier,
    fallback: String = "Music",
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
) {
    val artists = if (item.kind == YouTubeItemKind.VIDEO) musicArtistsFor(item) else emptyList()
    if (artists.isEmpty()) {
        Text(
            item.author?.takeIf { it.isNotBlank() } ?: fallback,
            fontSize = fontSize,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    val text = remember(artists, color) {
        val styles = TextLinkStyles(
            style = SpanStyle(color = color),
            hoveredStyle = SpanStyle(color = Color.White, textDecoration = TextDecoration.Underline),
        )
        buildAnnotatedString {
            artists.forEachIndexed { i, artist ->
                if (i > 0) append(", ")
                withLink(
                    LinkAnnotation.Clickable(tag = "artist:$i", styles = styles) {
                        openMusicArtist(artist.name, artist.id)
                    },
                ) { append(artist.name) }
            }
        }
    }
    Text(
        text,
        fontSize = fontSize,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
