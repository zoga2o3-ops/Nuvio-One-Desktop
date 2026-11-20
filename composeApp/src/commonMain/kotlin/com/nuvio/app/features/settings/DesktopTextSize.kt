package com.nuvio.app.features.settings

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_text_size_default
import nuvio.composeapp.generated.resources.settings_text_size_extra_large
import nuvio.composeapp.generated.resources.settings_text_size_large
import nuvio.composeapp.generated.resources.settings_text_size_small
import org.jetbrains.compose.resources.StringResource

/** Per-device desktop text scale. Default matches the current desktop size. */
enum class DesktopTextSize(
    val key: String,
    val scale: Float,
    val labelRes: StringResource,
) {
    SMALL("small", 1.08f, Res.string.settings_text_size_small),
    DEFAULT("default", 1.18f, Res.string.settings_text_size_default),
    LARGE("large", 1.30f, Res.string.settings_text_size_large),
    EXTRA_LARGE("extra_large", 1.42f, Res.string.settings_text_size_extra_large),
    ;

    companion object {
        fun fromKey(key: String?): DesktopTextSize =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}
