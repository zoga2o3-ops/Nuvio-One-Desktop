package com.nuvio.app.features.player

/**
 * Applies the subtitle-style controls sent by the native player overlay.
 *
 * OneTube and the Music video player draw their own controls instead of using
 * the common PlayerScreen, so they need to feed these events back into the
 * shared settings repository. The caller is responsible for re-applying the
 * resulting style to the native controller and for opening the font picker
 * when [onImportFont] is invoked.
 */
internal fun applySubtitleStyleControlEvent(
    type: String,
    value: Double,
    onImportFont: () -> Unit,
): Boolean {
    val style = PlayerSettingsRepository.uiState.value.subtitleStyle
    when (type) {
        "subtitleFontSizeDelta" -> PlayerSettingsRepository.setSubtitleStyle(
            style.copy(
                fontSizeSp = (style.fontSizeSp + value.toInt())
                    .coerceIn(subtitleFontSizeRangeSp),
            ),
        )

        "subtitleOutlineToggle" -> PlayerSettingsRepository.setSubtitleStyle(
            style.copy(outlineEnabled = !style.outlineEnabled),
        )

        "subtitleBoldToggle" -> PlayerSettingsRepository.setSubtitleStyle(
            style.copy(bold = !style.bold),
        )

        "subtitleBottomOffsetDelta" -> PlayerSettingsRepository.setSubtitleStyle(
            style.copy(
                bottomOffset = (style.bottomOffset + value.toInt()).coerceIn(0, 200),
            ),
        )

        "subtitleTextColor" -> SubtitleColorSwatches.getOrNull(value.toInt())?.let { color ->
            PlayerSettingsRepository.setSubtitleStyle(
                style.copy(textColor = color.copy(alpha = style.textColor.alpha)),
            )
        }

        "subtitleOutlineColor" -> SubtitleColorSwatches.getOrNull(value.toInt())?.let { color ->
            PlayerSettingsRepository.setSubtitleStyle(style.copy(outlineColor = color))
        }

        "subtitleBackgroundColor" -> SubtitleColorSwatches.getOrNull(value.toInt())?.let { color ->
            PlayerSettingsRepository.setSubtitleStyle(
                style.copy(backgroundColor = color.copy(alpha = style.backgroundColor.alpha)),
            )
        }

        "subtitleBackgroundOpacity" -> {
            val alpha = (value.toFloat() / 100f).coerceIn(0f, 1f)
            PlayerSettingsRepository.setSubtitleStyle(
                style.copy(backgroundColor = style.backgroundColor.copy(alpha = alpha)),
            )
        }

        "subtitleTextOpacity" -> {
            val alpha = (value.toFloat() / 100f).coerceIn(0f, 1f)
            PlayerSettingsRepository.setSubtitleStyle(
                style.copy(textColor = style.textColor.copy(alpha = alpha)),
            )
        }

        "subtitleFont" -> {
            val fonts = BuiltInSubtitleFonts + SubtitleFonts.installed().map { it.id }
            fonts.getOrNull(value.toInt())?.let { id ->
                PlayerSettingsRepository.setSubtitleStyle(
                    style.copy(appearance = style.appearance.copy(fontId = id)),
                )
            }
        }

        "subtitleFontRemove" -> {
            val fonts = SubtitleFonts.installed()
            fonts.getOrNull(value.toInt() - BuiltInSubtitleFonts.size)?.let { font ->
                SubtitleFonts.remove(font.id)
                if (style.appearance.fontId == font.id) {
                    PlayerSettingsRepository.setSubtitleStyle(
                        style.copy(appearance = style.appearance.copy(fontId = "default")),
                    )
                }
            }
        }

        "subtitleFontImport" -> onImportFont()

        "subtitleShadowToggle" -> {
            val appearance = style.appearance
            PlayerSettingsRepository.setSubtitleStyle(
                style.copy(
                    appearance = appearance.copy(shadowEnabled = !appearance.shadowEnabled),
                ),
            )
        }

        "subtitleShadowOpacityDelta" -> {
            val appearance = style.appearance
            PlayerSettingsRepository.setSubtitleStyle(
                style.copy(
                    appearance = appearance.copy(
                        shadowOpacity = (appearance.shadowOpacity + value.toInt()).coerceIn(0, 100),
                    ),
                ),
            )
        }

        "subtitleShadowSizeDelta" -> {
            val appearance = style.appearance
            PlayerSettingsRepository.setSubtitleStyle(
                style.copy(
                    appearance = appearance.copy(
                        shadowSize = (appearance.shadowSize + value.toInt()).coerceIn(1, 8),
                    ),
                ),
            )
        }

        "subtitleStyleReset" -> PlayerSettingsRepository.setSubtitleStyle(style.resetAppearance())

        else -> return false
    }
    return true
}
