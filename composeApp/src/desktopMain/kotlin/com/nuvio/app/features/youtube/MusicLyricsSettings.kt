package com.nuvio.app.features.youtube

import com.nuvio.app.core.ui.SectionPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.PaxSenix
import com.nuvio.app.core.storage.DesktopSecretStore
import com.nuvio.app.core.storage.DesktopStorage

/**
 * Which lyric sources Music asks, in what order, and whether a word-synced answer is worth
 * waiting for. BitChord's model (LyricsSource): every chosen source is asked at once, answers are
 * taken in this order. The PaxSenix API key is kept encrypted for this Windows user.
 */
internal object MusicLyricsSettings {
    private val store = DesktopStorage.store("nuvio_music_lyrics")

    var order by mutableStateOf(load())
        private set
    var enabled by mutableStateOf(loadEnabled())
        private set
    var preferWordSynced by mutableStateOf(store.getBoolean("prefer_word_synced") ?: false)
        private set
    var hasPaxSenixKey by mutableStateOf(false)
        private set

    init {
        val key = store.getString("paxsenix_key")?.let { runCatching { DesktopSecretStore.unprotect(it) }.getOrNull() }.orEmpty()
        PaxSenix.setApiKey(key)
        hasPaxSenixKey = key.isNotBlank()
    }

    private fun load(): List<LyricsSource> {
        val saved = store.getString("order")?.split(',')?.mapNotNull { name -> LyricsSource.entries.firstOrNull { it.name == name } }
        return LyricsSource.ordered(saved.orEmpty())
    }

    private fun loadEnabled(): Set<LyricsSource> {
        val saved = store.getString("enabled") ?: return LyricsSource.offered.toSet()
        return saved.split(',').mapNotNull { name -> LyricsSource.entries.firstOrNull { it.name == name } }.toSet()
    }

    fun setEnabled(source: LyricsSource, on: Boolean) {
        enabled = if (on) enabled + source else enabled - source
        store.putString("enabled", enabled.joinToString(",") { it.name })
        MusicLyricsRepository.clear()
    }

    fun move(source: LyricsSource, delta: Int) {
        val list = order.toMutableList()
        val from = list.indexOf(source)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        list.removeAt(from)
        list.add(to, source)
        order = list
        store.putString("order", list.joinToString(",") { it.name })
        MusicLyricsRepository.clear()
    }

    fun choosePreferWordSynced(on: Boolean) {
        preferWordSynced = on
        store.putBoolean("prefer_word_synced", on)
        MusicLyricsRepository.clear()
    }

    fun setPaxSenixKey(value: String) {
        val key = value.trim()
        PaxSenix.setApiKey(key)
        store.putString("paxsenix_key", key.takeIf { it.isNotBlank() }?.let(DesktopSecretStore::protect))
        hasPaxSenixKey = key.isNotBlank()
        MusicLyricsRepository.clear()
    }

    fun reset() {
        store.remove("order")
        store.remove("enabled")
        store.remove("prefer_word_synced")
        order = load()
        enabled = loadEnabled()
        preferWordSynced = false
        MusicLyricsRepository.clear()
    }
}

private val LyricsAccent = Color(0xFF9AA8BD)
private val LyricsDim: Color get() = SectionPalette.text
private val LyricsRow: Color get() = SectionPalette.raised

/** Music settings → Lyrics: pick sources, put them in order, prefer word-synced. */
@Composable
internal fun LyricsSourcesCard() {
    val settings = MusicLyricsSettings
    var keyDraft by remember { mutableStateOf("") }
    SettingsCard("Lyrics", "Where lyrics come from, in the order they're used", Icons.Filled.Subtitles) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Prefer word-by-word lyrics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Keep looking down the list for word-timed lyrics when a higher source only has whole lines.",
                    fontSize = 11.5.sp,
                    color = LyricsDim,
                )
            }
            Switch(
                checked = settings.preferWordSynced,
                onCheckedChange = settings::choosePreferWordSynced,
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF64748B)),
            )
        }
        Text(
            "All ticked sources are asked at once; the first in this list that has the song wins.",
            fontSize = 11.5.sp,
            color = LyricsDim,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            settings.order.forEachIndexed { index, source ->
                val on = source in settings.enabled
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(LyricsRow)
                        .clickable { settings.setEnabled(source, !on) }
                        .padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = on,
                        onCheckedChange = { settings.setEnabled(source, it) },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF64748B)),
                    )
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(source.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else LyricsDim.copy(alpha = 0.6f))
                            if (source.wordSynced) {
                                Text(
                                    "WORD",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LyricsAccent,
                                    modifier = Modifier.padding(start = 8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(LyricsAccent.copy(alpha = 0.15f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(source.detail, fontSize = 11.sp, color = LyricsDim.copy(alpha = if (on) 0.85f else 0.5f))
                    }
                    IconButton(onClick = { settings.move(source, -1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${source.label} up", tint = LyricsDim, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { settings.move(source, 1) }, enabled = index < settings.order.lastIndex, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${source.label} down", tint = LyricsDim, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Text(
            if (settings.hasPaxSenixKey) "PaxSenix API key saved (used by PaxSenix: Spotify and Musixmatch)."
            else "PaxSenix: Spotify and PaxSenix: Musixmatch need a PaxSenix API key.",
            fontSize = 11.5.sp,
            color = LyricsDim,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                singleLine = true,
                placeholder = { Text("PaxSenix API key", fontSize = 12.sp) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { settings.setPaxSenixKey(keyDraft); keyDraft = "" }, enabled = keyDraft.isNotBlank()) {
                Text("Save key", fontSize = 12.5.sp)
            }
            if (settings.hasPaxSenixKey) {
                TextButton(onClick = { settings.setPaxSenixKey("") }) { Text("Remove", fontSize = 12.5.sp, color = Color(0xFFFF8A9B)) }
            }
        }
        TextButton(onClick = settings::reset) { Text("Reset to default order", fontSize = 12.5.sp) }
    }
}
