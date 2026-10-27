package com.lazy.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LazyNight = Color(0xFF0C0A14)
val MistWhite = Color(0xFFF8F5FF)
val SoftText = Color(0xFFB2ABBF)
val Lavender = Color(0xFFD3BFFF)

/** Intentional light-rimmed translucency; ambient artwork below produces frosted depth. */
fun Modifier.glass(radius: Int = 26, bright: Boolean = false): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    // Restrained glass: shaded body, specular rim and an inner highlight.
    // Artwork is softened once underneath; each control stays a cheap drawing.
    return this.clip(shape)
        .background(
            Brush.linearGradient(
                if (bright) listOf(Color(0xFF776788).copy(alpha = 0.52f), Color(0xFF201D30).copy(alpha = 0.82f))
                else listOf(Color(0xFF554A66).copy(alpha = 0.42f), Color(0xFF1A1828).copy(alpha = 0.71f))
            )
        )
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = if (bright) 0.18f else 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.10f))
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Lavender.copy(alpha = 0.17f), Color.White.copy(alpha = 0.10f))),
            shape
        )
}

@Composable
fun AmbientHaze(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(color = LazyNight)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF48335E).copy(alpha = 0.67f), Color.Transparent), center = Offset(size.width * 0.87f, size.height * 0.10f), radius = size.width * 1.05f),
            center = Offset(size.width * 0.87f, size.height * 0.10f),
            radius = size.width * 1.05f
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF253E5E).copy(alpha = 0.28f), Color.Transparent), center = Offset(size.width * 0.03f, size.height * 0.82f), radius = size.width * 0.9f),
            center = Offset(size.width * 0.03f, size.height * 0.82f),
            radius = size.width * 0.9f
        )
    }
}
