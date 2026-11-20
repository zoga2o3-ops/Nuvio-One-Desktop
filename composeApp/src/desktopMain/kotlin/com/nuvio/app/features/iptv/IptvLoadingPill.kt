package com.nuvio.app.features.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small bottom-right loading pill, shown anywhere in the app while the Live TV
 * playlist or EPG cache is filling. Replaces the old blocking loading card.
 */
@Composable
internal fun IptvLoadingPill(modifier: Modifier = Modifier) {
    if (!IptvSession.loading) return
    val progress = IptvSession.progress
    val percent = ((progress?.fraction ?: 0f) * 100f).toInt().coerceIn(0, 100)

    Row(
        modifier.clip(RoundedCornerShape(999.dp))
            .background(Color(0xF21A1A22))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(999.dp))
            .padding(horizontal = 17.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(19.dp),
            color = Color(0xFFFF3B5C),
            strokeWidth = 2.4.dp,
        )
        Column(Modifier.widthIn(max = 360.dp)) {
            Text(
                progress?.title ?: "Loading playlist",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                progress?.detail ?: "Reading your Live TV sources",
                fontSize = 11.5.sp,
                color = Color(0xFFB6BCCB),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "$percent%",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B86),
        )
    }
}
