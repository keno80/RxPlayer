package com.keno.rxplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Artwork(index: Int, modifier: Modifier = Modifier) {
    val colors = when (index) {
        1 -> listOf(Color(0xFFEDC085), Color(0xFFAD4F31))
        2 -> listOf(Color(0xFF9B8CC8), Color(0xFF322E58))
        else -> listOf(Color(0xFF9BAF73), Color(0xFF29392F))
    }
    Box(
        modifier.clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .semantics { contentDescription = "原创几何封面" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension
            val center = Offset(size.width * .5f, size.height * .5f)
            when (index) {
                1 -> {
                    drawCircle(Color(0xFFFFE4B2), unit * .24f, Offset(size.width * .66f, size.height * .34f))
                    repeat(7) { row ->
                        val path = Path()
                        for (step in 0..60) {
                            val x = size.width * step / 60f
                            val y = size.height * (.49f + row * .075f) +
                                sin(step / 60f * PI * 2 + row * .3).toFloat() * unit * .07f
                            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, Color(0xFF653C30).copy(alpha = .6f), style = Stroke(unit * .028f))
                    }
                }
                2 -> {
                    rotate(-35f, center) {
                        repeat(4) { ring ->
                            val radius = unit * (.16f + ring * .085f)
                            drawOval(
                                color = Color(0xFFE7DCFF).copy(alpha = .8f - ring * .12f),
                                topLeft = center - Offset(radius, radius * .55f),
                                size = Size(radius * 2, radius * 1.1f),
                                style = Stroke(unit * .018f),
                            )
                        }
                    }
                    drawCircle(Color(0xFFEFDAA3), unit * .075f, center)
                    drawCircle(Color.White, unit * .023f, Offset(size.width * .77f, size.height * .23f))
                }
                else -> {
                    val flower = Path()
                    for (step in 0..240) {
                        val angle = step / 240.0 * PI * 2
                        val radius = unit * (.27f + .055f * cos(angle * 8).toFloat())
                        val x = center.x + cos(angle).toFloat() * radius
                        val y = center.y + sin(angle).toFloat() * radius
                        if (step == 0) flower.moveTo(x, y) else flower.lineTo(x, y)
                    }
                    flower.close()
                    drawPath(flower, Color(0xFFD5ED9B))
                    drawCircle(Color(0xFF364A30), unit * .12f, center)
                    drawCircle(Color(0xFFD5ED9B), unit * .025f, center)
                }
            }
        }
    }
}
