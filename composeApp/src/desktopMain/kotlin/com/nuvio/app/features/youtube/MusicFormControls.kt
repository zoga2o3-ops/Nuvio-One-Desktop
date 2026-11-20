package com.nuvio.app.features.youtube

import com.nuvio.app.core.ui.SectionPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val MusicFormAccent = Color(0xFF9AA8BD)
internal val MusicFormAccentFill = Color(0xFF64748B)
internal val MusicFormPanel: Color get() = SectionPalette.surface
internal val MusicFormBorder: Color get() = SectionPalette.border
internal val MusicFormText: Color get() = SectionPalette.text
internal val MusicFormDim: Color get() = SectionPalette.muted

/** A labelled, desktop-sized text field used by the Music settings and integration forms. */
@Composable
internal fun MusicFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    enabled: Boolean = true,
) {
    Column(modifier) {
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MusicFormDim,
            modifier = Modifier.padding(start = 1.dp, bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
            singleLine = true,
            enabled = enabled,
            textStyle = TextStyle(fontSize = 16.sp, color = MusicFormText),
            placeholder = {
                if (placeholder.isNotBlank()) {
                    Text(placeholder, fontSize = 15.sp, color = MusicFormDim.copy(alpha = 0.65f))
                }
            },
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MusicFormText,
                unfocusedTextColor = MusicFormText,
                disabledTextColor = MusicFormDim,
                focusedBorderColor = MusicFormAccent,
                unfocusedBorderColor = MusicFormBorder,
                disabledBorderColor = MusicFormBorder.copy(alpha = 0.5f),
                focusedContainerColor = MusicFormPanel,
                unfocusedContainerColor = MusicFormPanel,
                disabledContainerColor = MusicFormPanel.copy(alpha = 0.6f),
                cursorColor = MusicFormAccent,
                focusedPlaceholderColor = MusicFormDim,
                unfocusedPlaceholderColor = MusicFormDim,
            ),
        )
    }
}

/** Filled primary action for the Music forms. */
@Composable
internal fun MusicPrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (enabled) MusicFormAccentFill else MusicFormPanel)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else MusicFormDim,
        )
    }
}
