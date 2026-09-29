package com.yao.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@Composable
fun MinimalClock(
    modifier: Modifier = Modifier,
    showSeconds: Boolean = true,
    tone: Color = Color.White,
    clockBackgroundOpacity: Float = 0f,
    dayBackgroundOpacity: Float = 0f
) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            delay(1000)
        }
    }

    val hour = SimpleDateFormat("HH", Locale.getDefault()).format(now.time)
    val minute = SimpleDateFormat("mm", Locale.getDefault()).format(now.time)
    val seconds = SimpleDateFormat("ss", Locale.getDefault()).format(now.time)
    val day = SimpleDateFormat("EEEE", Locale("es", "CO"))
        .format(now.time).uppercase(Locale("es", "CO"))

    val outline = if (tone.luminance() > 0.5f) Color.Black else Color.White

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .width(58.dp)
                .background(
                    Color.Black.copy(alpha = dayBackgroundOpacity.coerceIn(0f, 1f)),
                    RoundedCornerShape(18.dp)
                )
                .padding(vertical = 8.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val middle = (day.length - 1) / 2f
            day.forEachIndexed { index, c ->
                val d = abs(index - middle) / middle.coerceAtLeast(1f)
                // El nombre del día ahora va de gris claro a blanco y vuelve a gris claro.
                // Ya no depende de la tonalidad negra/blanca del reloj ni usa transparencia
                // para simular el degradado.
                val dayColor = lerp(
                    start = Color(0xFFC8C8C8),
                    stop = Color.White,
                    fraction = (1f - d).coerceIn(0f, 1f)
                )
                OutlinedClockText(
                    text = c.toString(),
                    color = dayColor,
                    outline = Color(0xFF555555),
                    fontSize = 29.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.width(18.dp))

        Column(
            modifier = Modifier
                .width(205.dp)
                .background(
                    Color.Black.copy(alpha = clockBackgroundOpacity.coerceIn(0f, 1f)),
                    RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedClockText(
                text = hour,
                color = tone,
                outline = outline,
                fontSize = 84.sp,
                lineHeight = 78.sp,
                fontWeight = FontWeight.Light
            )

            // Outlined divider: dark/light contour plus the selected tone.
            Box(
                Modifier
                    .fillMaxWidth(.92f)
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, outline, Color.Transparent)
                        )
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .align(Alignment.Center)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, tone, Color.Transparent)
                            )
                        )
                )
            }

            OutlinedClockText(
                text = minute,
                color = tone,
                outline = outline,
                fontSize = 84.sp,
                lineHeight = 78.sp,
                fontWeight = FontWeight.Light
            )

            if (showSeconds) {
                OutlinedClockText(
                    text = seconds,
                    color = tone,
                    outline = outline,
                    fontSize = 18.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
private fun OutlinedClockText(
    text: String,
    color: Color,
    outline: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Box(contentAlignment = Alignment.Center) {
        // Contorno de alto contraste (8 direcciones) según tono blanco/negro
        // para números, letras y líneas del reloj minimalista.
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
                lineHeight = lineHeight,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                modifier = offsetModifier
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center
        )
    }
}
