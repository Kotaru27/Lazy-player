package com.lazy.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Slate and opal; no saturated purple wash.
val LazyNight = Color(0xFF0B1016)
val MistWhite = Color(0xFFF7F9FB)
val SoftText = Color(0xFFAEBDC9)
val Lavender = Color(0xFFDDECF4)

/**
 * Three-layer glass approximation: smoked translucent body, bright upper specular
 * rim, soft shadow. Neither this nor Compose claims real backdrop refraction.
 */
fun Modifier.glass(radius: Int = 26, bright: Boolean = false): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    // Only mini-player and bottom navigation should use this strong layer.
    // Blur/refraction is deliberately not faked by stacking opaque gradients.
    return this
        .clip(shape)
        .background(Color(0xFF809CAA).copy(alpha = if (bright) 0.21f else 0.14f))
        .background(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (bright) 0.10f else 0.06f),
                0.36f to Color.White.copy(alpha = 0.02f),
                1f to Color.Black.copy(alpha = 0.07f)
            )
        )
        .border(
            0.7.dp,
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (bright) 0.33f else 0.24f),
                0.42f to Color.White.copy(alpha = 0.10f),
                1f to Color.White.copy(alpha = 0.045f)
            ),
            shape
        )
}

@Composable
fun AmbientHaze(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(LazyNight)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF365B6C).copy(alpha = 0.34f), Color.Transparent),
                center = Offset(size.width * 0.91f, size.height * 0.12f),
                radius = size.width * 0.85f
            ),
            center = Offset(size.width * 0.91f, size.height * 0.12f),
            radius = size.width * 0.85f
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF364959).copy(alpha = 0.21f), Color.Transparent),
                center = Offset(size.width * 0.03f, size.height * 0.94f),
                radius = size.width * 0.90f
            ),
            center = Offset(size.width * 0.03f, size.height * 0.94f),
            radius = size.width * 0.90f
        )
    }
}
