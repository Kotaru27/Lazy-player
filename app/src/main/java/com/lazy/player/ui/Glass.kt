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
val LazyNight = Color(0xFF0B0D10)
val MistWhite = Color(0xFFF3F5F7)
val SoftText = Color(0xFFADB5BF)
val Lavender = Color(0xFFE6EAED)

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
        .background(Color(0xFFA4AEBB).copy(alpha = if (bright) 0.12f else 0.08f))
        .background(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (bright) 0.085f else 0.04f),
                0.36f to Color.White.copy(alpha = 0.02f),
                1f to Color.Black.copy(alpha = 0.07f)
            )
        )
        .border(
            0.7.dp,
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (bright) 0.24f else 0.17f),
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
                listOf(Color(0xFF363B46).copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * 0.91f, size.height * 0.12f),
                radius = size.width * 0.85f
            ),
            center = Offset(size.width * 0.91f, size.height * 0.12f),
            radius = size.width * 0.85f
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF2D333C).copy(alpha = 0.15f), Color.Transparent),
                center = Offset(size.width * 0.03f, size.height * 0.94f),
                radius = size.width * 0.90f
            ),
            center = Offset(size.width * 0.03f, size.height * 0.94f),
            radius = size.width * 0.90f
        )
    }
}
