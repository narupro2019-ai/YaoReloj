package com.yao.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ClassicClock(
    modifier: Modifier = Modifier,
    showSeconds: Boolean = true,
    tone: Color = Color.White,
    clockBackgroundOpacity: Float = 0f,
    dayBackgroundOpacity: Float = 0f
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000)
        }
    }

    val hour = currentTime.get(Calendar.HOUR)
    val minute = currentTime.get(Calendar.MINUTE)
    val second = currentTime.get(Calendar.SECOND)

    val outline = if (tone.luminance() > 0.5f) Color.Black else Color.White
    val day = SimpleDateFormat("EEEE", Locale("es", "CO"))
        .format(currentTime.time).uppercase(Locale("es", "CO"))

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fondo independiente del reloj: 0 = transparente, 1 = sólido.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(10.dp)
                    .background(
                        Color.Black.copy(alpha = clockBackgroundOpacity.coerceIn(0f, 1f)),
                        RoundedCornerShape(28.dp)
                    )
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // Outer contour for the clock face.
            drawCircle(
                color = outline,
                radius = radius + 1.5.dp.toPx(),
                center = center,
                style = Stroke(width = 7.dp.toPx())
            )
            drawCircle(
                color = tone,
                radius = radius,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            for (i in 0 until 60) {
                val angle = Math.toRadians(i * 6.0 - 90)
                val isHour = i % 5 == 0
                val outer = radius * .92f
                val inner = if (isHour) radius * .80f else radius * .87f
                val start = Offset(
                    center.x + cos(angle).toFloat() * inner,
                    center.y + sin(angle).toFloat() * inner
                )
                val end = Offset(
                    center.x + cos(angle).toFloat() * outer,
                    center.y + sin(angle).toFloat() * outer
                )

                // Contour line followed by the selected tone.
                drawLine(
                    color = outline,
                    start = start,
                    end = end,
                    strokeWidth = if (isHour) 6.dp.toPx() else 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tone,
                    start = start,
                    end = end,
                    strokeWidth = if (isHour) 3.5.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            fun drawHand(angleDegrees: Float, length: Float, width: Float) {
                rotate(angleDegrees, center) {
                    drawLine(
                        color = outline,
                        start = Offset(center.x, center.y + radius * .09f),
                        end = Offset(center.x, center.y - radius * length),
                        strokeWidth = (width + 3.dp.toPx()),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = tone,
                        start = Offset(center.x, center.y + radius * .09f),
                        end = Offset(center.x, center.y - radius * length),
                        strokeWidth = width,
                        cap = StrokeCap.Round
                    )
                }
            }

            drawHand((hour % 12 + minute / 60f) * 30f, .48f, 5.dp.toPx())
            drawHand((minute + second / 60f) * 6f, .67f, 3.dp.toPx())

            if (showSeconds) {
                rotate(second * 6f, center) {
                    drawLine(
                        color = outline,
                        start = Offset(center.x, center.y + radius * .15f),
                        end = Offset(center.x, center.y - radius * .76f),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.Red,
                        start = Offset(center.x, center.y + radius * .15f),
                        end = Offset(center.x, center.y - radius * .76f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

                    drawCircle(outline, 9.dp.toPx(), center)
                    drawCircle(Color.Red, 6.dp.toPx(), center)
                }
            }

            // Nombre del día debajo del reloj, centrado respecto a la esfera.
            // Mantiene el formato horizontal y el mismo degradado del reloj minimalista:
            // gris claro -> blanco -> gris claro.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                GradientDayText(
                    text = day,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GradientDayText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight
) {
    val middle = (text.length - 1) / 2f

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEachIndexed { index, character ->
            val d = abs(index - middle) / middle.coerceAtLeast(1f)
            val dayColor = lerp(
                start = Color(0xFFC8C8C8),
                stop = Color.White,
                fraction = (1f - d).coerceIn(0f, 1f)
            )

            OutlinedClockText(
                text = character.toString(),
                color = dayColor,
                outline = Color(0xFF555555),
                fontSize = fontSize,
                fontWeight = fontWeight
            )
        }
    }
}

@Composable
private fun OutlinedClockText(
    text: String,
    color: Color,
    outline: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Box(contentAlignment = Alignment.Center) {
        listOf(
            Modifier.offset(x = 1.dp),
            Modifier.offset(x = (-1).dp),
            Modifier.offset(y = 1.dp),
            Modifier.offset(y = (-1).dp),
            Modifier.offset(x = 1.dp, y = 1.dp),
            Modifier.offset(x = 1.dp, y = (-1).dp),
            Modifier.offset(x = (-1).dp, y = 1.dp),
            Modifier.offset(x = (-1).dp, y = (-1).dp)
        ).forEach { offsetModifier ->
            Text(
                text = text,
                color = outline,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                modifier = offsetModifier
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center
        )
    }
}
