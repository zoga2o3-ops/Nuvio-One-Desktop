package com.nuvio.app.features.youtube

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/** Draws a QR code for the party/phone URLs so a guest can scan instead of typing. */
@Composable
internal fun MusicQrCode(content: String?, modifier: Modifier = Modifier) {
    val matrix = remember(content) {
        content?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching {
                MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 360, 360)
            }.getOrNull()
        }
    }
    if (matrix == null) return
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(8.dp),
    ) {
        QrMatrixCanvas(matrix, Modifier.fillMaxSize())
    }
}

@Composable
private fun QrMatrixCanvas(matrix: BitMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = minOf(size.width / matrix.width, size.height / matrix.height)
        val left = (size.width - cell * matrix.width) / 2f
        val top = (size.height - cell * matrix.height) / 2f
        drawRect(Color.White, size = size)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(left + x * cell, top + y * cell),
                        size = Size(cell + 0.2f, cell + 0.2f),
                    )
                }
            }
        }
    }
}
