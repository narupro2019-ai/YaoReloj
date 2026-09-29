package com.yao.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yao.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Familia tipográfica Didone (Playfair Display) para el reloj de panel completo. */
private val DidoneFontFamily = FontFamily(
    Font(R.font.playfair_display_regular, FontWeight.Normal),
    Font(R.font.playfair_display_bold, FontWeight.Bold)
)

/**
 * Reloj estilo Didone según la referencia visual:
 *
 *     2  0     ← hora: 2.º dígito un poco más abajo (diagonal)
 *     3  0     ← minutos: misma disposición diagonal
 *
 * - Filas casi pegadas.
 * - Degradado: hora arriba→abajo, minutos abajo→arriba.
 * - Día en vertical bajo el reloj, con contorno.
 */
@Composable
fun DidoneClock(
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

    val hour = SimpleDateFormat("HH", Locale.getDefault()).format(now.time).padStart(2, '0')
    val minute = SimpleDateFormat("mm", Locale.getDefault()).format(now.time).padStart(2, '0')
    val seconds = SimpleDateFormat("ss", Locale.getDefault()).format(now.time)
    val day = SimpleDateFormat("EEEE", Locale("es", "CO"))
        .format(now.time)
        .uppercase(Locale("es", "CO"))

    val outline = if (tone.luminance() > 0.5f) Color.Black else Color.White
    val veilAlpha = clockBackgroundOpacity.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .background(
                Color.White.copy(alpha = veilAlpha * 0.22f),
                RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        val panelWidthDp = maxWidth
        val panelHeightDp = maxHeight
        val digitSize = (panelWidthDp.value * 0.48f).coerceIn(52f, 120f).sp
        val letterSize = (panelHeightDp.value * 0.055f).coerceIn(18f, 36f).sp
        // Cuánto baja el 2.º dígito respecto al 1.º (diagonal suave).
        val diagonalDrop = (digitSize.value * 0.28f).dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hora y minutos: dos filas diagonales casi pegadas (como en la imagen).
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((-6).dp)
            ) {
                // Hora — degradado arriba → abajo
                DiagonalDigitRow(
                    text = hour,
                    fontSize = digitSize,
                    diagonalDrop = diagonalDrop,
                    brush = Brush.verticalGradient(
                        listOf(tone.copy(alpha = 1f), tone.copy(alpha = 0.35f))
                    ),
                    outline = outline
                )

                // Minutos — degradado abajo → arriba
                DiagonalDigitRow(
                    text = minute,
                    fontSize = digitSize,
                    diagonalDrop = diagonalDrop,
                    brush = Brush.verticalGradient(
                        listOf(tone.copy(alpha = 0.35f), tone.copy(alpha = 1f))
                    ),
                    outline = outline
                )

                if (showSeconds) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = seconds,
                        style = TextStyle(
                            brush = Brush.verticalGradient(
                                listOf(tone.copy(alpha = 0.85f), tone.copy(alpha = 0.45f))
                            ),
                            fontSize = (digitSize.value * 0.22f).coerceIn(12f, 24f).sp,
                            fontFamily = DidoneFontFamily,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (day.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 10.dp)
                        .background(
                            Color.Black.copy(alpha = dayBackgroundOpacity.coerceIn(0f, 1f) * 0.35f),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        day.forEach { char ->
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
                                        text = char.toString(),
                                        color = outline.copy(alpha = 0.80f),
                                        fontSize = letterSize,
                                        lineHeight = letterSize,
                                        fontFamily = DidoneFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = offsetModifier
                                    )
                                }
                                Text(
                                    text = char.toString(),
                                    color = tone.copy(alpha = 0.92f),
                                    fontSize = letterSize,
                                    lineHeight = letterSize,
                                    fontFamily = DidoneFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Fila de 2 dígitos en diagonal suave, como en la imagen de referencia:
 * el primer dígito más arriba, el segundo un poco más abajo a la derecha.
 *
 *     2
 *       0
 */
@Composable
private fun DiagonalDigitRow(
    text: String,
    fontSize: TextUnit,
    diagonalDrop: androidx.compose.ui.unit.Dp,
    brush: Brush,
    outline: Color
) {
    val padded = text.padStart(2, '0')
    val left = padded[0].toString()
    val right = padded[1].toString()
    val rowHeight = (fontSize.value * 1.35f).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Center
        ) {
            // Primer dígito (más arriba)
            Box(modifier = Modifier.offset(y = 0.dp)) {
                GradientOutlinedDigit(left, brush, outline, fontSize)
            }
            Spacer(Modifier.width((fontSize.value * 0.06f).dp))
            // Segundo dígito (un poco más abajo → diagonal)
            Box(modifier = Modifier.offset(y = diagonalDrop)) {
                GradientOutlinedDigit(right, brush, outline, fontSize)
            }
        }
    }
}

@Composable
private fun GradientOutlinedDigit(
    digit: String,
    brush: Brush,
    outline: Color,
    fontSize: TextUnit
) {
    val style = TextStyle(
        brush = brush,
        fontSize = fontSize,
        fontFamily = DidoneFontFamily,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    val outlineStyle = TextStyle(
        color = outline.copy(alpha = 0.70f),
        fontSize = fontSize,
        fontFamily = DidoneFontFamily,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Box(contentAlignment = Alignment.Center) {
        listOf(
            Modifier.offset(x = 1.5.dp),
            Modifier.offset(x = (-1.5).dp),
            Modifier.offset(y = 1.5.dp),
            Modifier.offset(y = (-1.5).dp),
            Modifier.offset(x = 1.dp, y = 1.dp),
            Modifier.offset(x = 1.dp, y = (-1).dp),
            Modifier.offset(x = (-1).dp, y = 1.dp),
            Modifier.offset(x = (-1).dp, y = (-1).dp)
        ).forEach { offsetModifier ->
            Text(
                text = digit,
                style = outlineStyle,
                maxLines = 1,
                softWrap = false,
                modifier = offsetModifier
            )
        }
        Text(
            text = digit,
            style = style,
            maxLines = 1,
            softWrap = false
        )
    }
}
