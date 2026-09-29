@file:OptIn(ExperimentalFoundationApi::class)

package com.yao.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yao.clock.ClassicClock
import com.yao.clock.DidoneClock
import com.yao.clock.MinimalClock
import com.yao.data.*
import kotlinx.coroutines.launch
import com.yao.ExternalWallpaperRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class LauncherApp(val name: String, val packageName: String, val icon: Drawable)

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LauncherScreen(
    externalWallpaperRequest: ExternalWallpaperRequest? = null,
    homePressToken: Long = 0L,
    onRequestDefaultLauncher: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }

    var locationPermissionGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        locationPermissionGranted =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    var weather by remember { mutableStateOf<WeatherState>(WeatherState.Loading) }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) {
            weather = WeatherState.PermissionRequired
            return@LaunchedEffect
        }

        while (true) {
            weather = try {
                val location = getCurrentLocation(context)
                    ?: throw IllegalStateException("No se pudo obtener la ubicación")
                fetchCurrentWeather(location.latitude, location.longitude, context)
            } catch (_: Exception) {
                WeatherState.Unavailable
            }
            kotlinx.coroutines.delay(30 * 60 * 1000L)
        }
    }

    val style by prefs.clockStyle.collectAsState(initial = SavedClockStyle.CLASSIC)
    val seconds by prefs.showSeconds.collectAsState(initial = true)
    val bgMode by prefs.backgroundMode.collectAsState(initial = BackgroundMode.SOLID)
    val bgColor by prefs.backgroundColor.collectAsState(initial = "#07131F")
    val bgImage by prefs.backgroundImage.collectAsState(initial = null)
    val tone by prefs.textTone.collectAsState(initial = TextTone.WHITE)
    val leftPanelOpacity by prefs.leftPanelOpacity.collectAsState(initial = 0f)
    val clockOpacity by prefs.clockOpacity.collectAsState(initial = 0f)
    val dayOpacity by prefs.dayOpacity.collectAsState(initial = 0f)

    // Cuando otra aplicación comparte/abre una imagen con este launcher,
    // la copiamos al almacenamiento privado de la app. Así el fondo sigue
    // disponible incluso si la URI original era temporal o el proveedor
    // (Galería/Zedge) revoca posteriormente el permiso.
    LaunchedEffect(externalWallpaperRequest?.token) {
        val request = externalWallpaperRequest ?: return@LaunchedEffect
        val localImage = importExternalWallpaper(context, request.uri)
        if (localImage != null) {
            prefs.setBackgroundImage(localImage)
            prefs.setBackgroundMode(BackgroundMode.IMAGE)
        }
    }

    var settings by remember { mutableStateOf(false) }
    // La lista de aplicaciones no puede ser inmutable durante toda la vida del
    // launcher: al volver del diálogo de desinstalación debemos consultar de
    // nuevo PackageManager para retirar inmediatamente el icono eliminado.
    var apps by remember { mutableStateOf(loadApps(context.packageManager)) }
    val quickAccessPkgs by prefs.quickAccessPackages.collectAsState(initial = emptyList())
    val recentPkgs by prefs.recentPackages.collectAsState(initial = emptyList())
    var showAddQuickAccess by remember { mutableStateOf(false) }
    // Página 0 = pantalla principal del launcher (reloj + apps).
    // Página 1 = accesos rápidos + recientes (se revela al deslizar a la IZQUIERDA).
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    // Al pulsar Home del sistema, volver siempre a la pantalla principal.
    LaunchedEffect(homePressToken) {
        if (homePressToken != 0L && pagerState.currentPage != 0) {
            pagerState.animateScrollToPage(0)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                apps = loadApps(context.packageManager)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Algunos proveedores no ofrecen permisos persistentes; la URI
                // sigue siendo válida durante la sesión actual.
            }
            scope.launch {
                prefs.setBackgroundImage(selectedUri.toString())
                prefs.setBackgroundMode(BackgroundMode.IMAGE)
            }
        }
    }

    val backgroundSolid = try {
        Color(android.graphics.Color.parseColor(bgColor))
    } catch (_: Exception) {
        Color(0xFF07131F)
    }

    val foreground = if (tone == TextTone.WHITE) Color.White else Color(0xFF101010)
    val muted = foreground.copy(alpha = .70f)


    // Bitmap del fondo (si hay imagen). Se calcula una sola vez y se reutiliza
    // tanto para el fondo nítido como para el cristal empañado del panel izquierdo.
    val wallpaperBitmap = remember(bgImage, bgMode) {
        if (bgMode != BackgroundMode.IMAGE || bgImage == null) return@remember null
        val imageKey = bgImage ?: return@remember null
        try {
            when (imageKey) {
                "preset:forest" -> android.graphics.BitmapFactory.decodeResource(
                    context.resources, com.yao.R.drawable.bg_minimal_forest
                )
                "preset:bulb" -> android.graphics.BitmapFactory.decodeResource(
                    context.resources, com.yao.R.drawable.bg_minimal_bulb
                )
                "preset:pulse" -> android.graphics.BitmapFactory.decodeResource(
                    context.resources, com.yao.R.drawable.bg_minimal_pulse
                )
                "preset:lampara" -> android.graphics.BitmapFactory.decodeResource(
                    context.resources, com.yao.R.drawable.lampara
                )
                "preset:classic_spheres" -> android.graphics.BitmapFactory.decodeResource(
                    context.resources, com.yao.R.drawable.bg_classic_spheres
                )
                "preset:classic_veil" -> android.graphics.BitmapFactory.decodeResource(
                    context.resources, com.yao.R.drawable.bg_classic_veil
                )
                "preset:didone_blue" -> android.graphics.BitmapFactory.decodeResource(
                    context.resources, com.yao.R.drawable.bg_didone_blue
                )
                "preset:didone_purple" -> android.graphics.BitmapFactory.decodeResource(
                    context.resources, com.yao.R.drawable.bg_didone_purple
                )
                "preset:didone_red" -> android.graphics.BitmapFactory.decodeResource(
                    context.resources, com.yao.R.drawable.bg_didone_red
                )
                else -> {
                    if (imageKey.startsWith("local:")) {
                        File(imageKey.removePrefix("local:")).inputStream().use {
                            android.graphics.BitmapFactory.decodeStream(it)
                        }
                    } else {
                        context.contentResolver.openInputStream(Uri.parse(imageKey))?.use {
                            android.graphics.BitmapFactory.decodeStream(it)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Fondo siempre nítido a pantalla completa (sin desenfoque global).
        // El efecto de cristal empañado solo se dibuja en la página principal.
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize().background(backgroundSolid))
        }

        // El fondo "Pulso" es claro/blanco en su borde superior derecho, lo que
        // deja los iconos de señal, batería, etc. de la barra de estado poco
        // legibles. Solo para este fondo se agrega un velo oscuro exclusivo
        // detrás de la barra de estado para recuperar el contraste.
        if (bgMode == BackgroundMode.IMAGE && bgImage == "preset:pulse") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .height(
                        with(androidx.compose.ui.platform.LocalDensity.current) {
                            WindowInsets.statusBars.getTop(this).toDp() + 26.dp
                        }
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }

        // Dos páginas horizontales:
        //   0 → Pantalla principal (calendario / apps / reloj)
        //   1 → Accesos rápidos + usados recientemente (se revela al deslizar a la IZQUIERDA)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            if (page == 1) {
                QuickAccessPage(
                    apps = apps,
                    quickAccessPkgs = quickAccessPkgs,
                    recentPkgs = recentPkgs,
                    foreground = foreground,
                    muted = muted,
                    onRecordRecent = { pkg ->
                        scope.launch { prefs.recordRecentApp(pkg) }
                    },
                    onRemoveQuickAccess = { pkg ->
                        scope.launch { prefs.removeQuickAccess(pkg) }
                    },
                    onRequestAdd = { showAddQuickAccess = true }
                )
            } else {
        // Layout: 40% para calendario/tiempo/aplicaciones y 60% para el reloj.
        // El antiguo contenedor del panel izquierdo se elimina: su contenido
        // queda directamente sobre el fondo general de la aplicación.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Cristal empañado SOLO en la página principal y SOLO con reloj minimalista.
            // No se aplica a la página de accesos rápidos (page 0).
            // Se usa la misma imagen de fondo, alineada con ContentScale.Crop,
            // recortada al panel izquierdo + velo translúcido para simular vaho.
            // Cristal empañado en minimalista y Didone (mismo velo translúcido).
            if ((style == SavedClockStyle.MINIMAL || style == SavedClockStyle.DIDONE) &&
                wallpaperBitmap != null
            ) {
                val screenW = maxWidth
                val screenH = maxHeight
                // Misma geometría que el layout: padding horizontal 7.dp +
                // peso 0.40f del área útil (maxWidth - 14.dp - 9.dp de divisor/spacer).
                val dividerX = (maxWidth - 14.dp - 9.dp) * 0.40f + 7.dp

                Box(
                    modifier = Modifier
                        .width(dividerX)
                        .fillMaxHeight()
                        .clipToBounds()
                ) {
                    // Imagen a tamaño completo de pantalla, alineada TopStart,
                    // para que la porción visible coincida exactamente con el
                    // fondo nítido (evita el efecto de "imagen repetida/desplazada").
                    Image(
                        bitmap = wallpaperBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .requiredWidth(screenW)
                            .requiredHeight(screenH)
                            .align(Alignment.TopStart)
                            .blur(14.dp),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    )
                    // Velo de cristal empañado: blanco suave + degradado para
                    // simular condensación sin tapar por completo el fondo.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.18f),
                                        Color.White.copy(alpha = 0.10f),
                                        Color.White.copy(alpha = 0.16f)
                                    )
                                )
                            )
                    )
                    // Suaviza el borde derecho hacia la línea divisora.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(28.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.06f)
                                    )
                                )
                            )
                    )
                }
            }

        Row(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.40f)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
                    .background(
                        Color.Black.copy(alpha = leftPanelOpacity),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ReferenceCalendar(foreground, muted)

                    SectionDivider(foreground)

                    WeatherBlock(weather, foreground, muted)

                    SectionDivider(foreground)

                    OutlinedText(
                        text = "APLICACIONES",
                        color = foreground.copy(.82f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)
                    )

                    // Cavidad física para el carrusel de aplicaciones.
                    // El grid conserva exactamente 2 columnas, el orden de `apps`
                    // y el tamaño original de cada icono; solo cambia el contenedor
                    // para darle profundidad y apariencia de túnel iluminado.
                    ApplicationCavity(
                        modifier = Modifier.weight(1f),
                        foreground = foreground,
                        muted = muted
                    ) {
                        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .fadingEdges(24.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(apps, key = { it.packageName }) {
                                    AppIcon(it, foreground) { pkg ->
                                        scope.launch { prefs.recordRecentApp(pkg) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Línea divisora exactamente en la separación 40% / 60%.
            // El degradado vertical reproduce el estilo suave del nombre del día:
            // transparente en los extremos y más visible en el centro.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                foreground.copy(alpha = 0f),
                                foreground.copy(alpha = 0.18f),
                                foreground.copy(alpha = 0.55f),
                                foreground.copy(alpha = 0.18f),
                                foreground.copy(alpha = 0f)
                            )
                        )
                    )
            )

            Spacer(Modifier.width(8.dp))

            // Right area: 60% del ancho total para el reloj.
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(0.60f)
            ) {
                when (style) {
                    SavedClockStyle.CLASSIC -> ClassicClock(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.40f)
                            .padding(horizontal = 12.dp),
                        showSeconds = seconds,
                        tone = foreground,
                        clockBackgroundOpacity = clockOpacity,
                        dayBackgroundOpacity = dayOpacity
                    )
                    SavedClockStyle.MINIMAL -> MinimalClock(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 5.dp),
                        showSeconds = seconds,
                        tone = foreground,
                        clockBackgroundOpacity = clockOpacity,
                        dayBackgroundOpacity = dayOpacity
                    )
                    SavedClockStyle.DIDONE -> DidoneClock(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 0.dp, vertical = 2.dp),
                        showSeconds = seconds,
                        tone = foreground,
                        clockBackgroundOpacity = clockOpacity,
                        dayBackgroundOpacity = dayOpacity
                    )
                }
            }
        }

        // Accesos rápidos del launcher: cámara y teléfono quedan justo encima
        // de configuración y comparten el mismo lenguaje visual del engranaje.
        val shortcutOutline = if (foreground.luminance() > 0.5f) Color.Black else Color.White
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = 7.dp, bottom = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            LauncherShortcutButton(
                contentDescription = "Cámara",
                foreground = foreground,
                outline = shortcutOutline,
                onClick = {
                    openRealCamera(context)
                },
                icon = ShortcutIcon.CAMERA
            )
            LauncherShortcutButton(
                contentDescription = "Teléfono",
                foreground = foreground,
                outline = shortcutOutline,
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL))
                    } catch (_: Exception) {
                        // Si no existe marcador, no hacemos nada.
                    }
                },
                icon = ShortcutIcon.PHONE
            )
            LauncherShortcutButton(
                contentDescription = "Configuración",
                foreground = foreground,
                outline = shortcutOutline,
                onClick = { settings = true },
                icon = ShortcutIcon.SETTINGS
            )
        }
        } // fin Box página principal
            } // fin else página 1
        } // fin HorizontalPager

        if (showAddQuickAccess) {
            AddQuickAccessDialog(
                apps = apps,
                alreadyAdded = quickAccessPkgs.toSet(),
                foreground = foreground,
                onDismiss = { showAddQuickAccess = false },
                onAdd = { pkg ->
                    scope.launch { prefs.addQuickAccess(pkg) }
                    showAddQuickAccess = false
                }
            )
        }

        if (settings) {
            // Modal de configuración: esta capa ocupa toda la pantalla y
            // recibe los toques fuera del panel. Así los eventos no llegan
            // a los iconos del launcher que quedan debajo.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                SettingsPanel(
                    style = style,
                seconds = seconds,
                bgMode = bgMode,
                bgColor = bgColor,
                bgImage = bgImage,
                tone = tone,
                leftPanelOpacity = leftPanelOpacity,
                clockOpacity = clockOpacity,
                dayOpacity = dayOpacity,
                foreground = foreground,
                onStyle = { scope.launch { prefs.setClockStyle(it) } },
                onSeconds = { scope.launch { prefs.setShowSeconds(it) } },
                onBgMode = { scope.launch { prefs.setBackgroundMode(it) } },
                onBgColor = { scope.launch { prefs.setBackgroundColor(it) } },
                onPickImage = { picker.launch(arrayOf("image/*")) },
                onPresetBackground = { key ->
                    scope.launch {
                        prefs.setBackgroundImage(key)
                        prefs.setBackgroundMode(BackgroundMode.IMAGE)
                    }
                },
                onTone = { scope.launch { prefs.setTextTone(it) } },
                onLeftPanelOpacity = { value -> scope.launch { prefs.setLeftPanelOpacity(value) } },
                onClockOpacity = { value -> scope.launch { prefs.setClockOpacity(value) } },
                onDayOpacity = { value -> scope.launch { prefs.setDayOpacity(value) } },
                    onDefaultLauncher = onRequestDefaultLauncher,
                    onClose = { settings = false }
                )
            }
        }
    }
}

private enum class ShortcutIcon { SETTINGS, CAMERA, PHONE }

@Composable
private fun LauncherShortcutButton(
    contentDescription: String,
    foreground: Color,
    outline: Color,
    onClick: () -> Unit,
    icon: ShortcutIcon
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // El mismo doble trazo usado por el engranaje anterior: un contorno oscuro
        // para separar el icono del fondo y un trazo principal luminoso encima.
        ShortcutIconCanvas(icon, outline, 2.8f)
        ShortcutIconCanvas(icon, foreground, 1.9f)
    }
}

@Composable
private fun ShortcutIconCanvas(icon: ShortcutIcon, color: Color, strokeWidth: Float) {
    Canvas(Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val sw = strokeWidth * density
        val centerX = w / 2f
        val centerY = h / 2f

        when (icon) {
            ShortcutIcon.SETTINGS -> {
                // Engranaje minimalista, coherente con la estética del launcher.
                drawCircle(color = color, radius = 7.0f * density, center = androidx.compose.ui.geometry.Offset(centerX, centerY), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                drawCircle(color = color, radius = 2.5f * density, center = androidx.compose.ui.geometry.Offset(centerX, centerY), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                repeat(8) { i ->
                    val angle = Math.toRadians((i * 45.0) - 90.0)
                    val x1 = centerX + kotlin.math.cos(angle).toFloat() * 8.2f * density
                    val y1 = centerY + kotlin.math.sin(angle).toFloat() * 8.2f * density
                    val x2 = centerX + kotlin.math.cos(angle).toFloat() * 11.0f * density
                    val y2 = centerY + kotlin.math.sin(angle).toFloat() * 11.0f * density
                    drawLine(color, androidx.compose.ui.geometry.Offset(x1, y1), androidx.compose.ui.geometry.Offset(x2, y2), sw, cap = StrokeCap.Square)
                }
            }
            ShortcutIcon.CAMERA -> {
                val left = 3.5f * density
                val top = 8f * density
                val right = 26.5f * density
                val bottom = 23f * density
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * density, 3f * density),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
                )
                val lens = androidx.compose.ui.geometry.Offset(centerX, 15.5f * density)
                drawCircle(color, 5f * density, lens, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                drawCircle(color, 2f * density, lens, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(9f * density, 5f * density),
                    size = androidx.compose.ui.geometry.Size(7f * density, 4f * density),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * density, 1.5f * density),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
                )
            }
            ShortcutIcon.PHONE -> {
                val path = Path().apply {
                    moveTo(9f * density, 4.5f * density)
                    cubicTo(7f * density, 5f * density, 6.2f * density, 7f * density, 7.2f * density, 10f * density)
                    cubicTo(9f * density, 15.5f * density, 14.5f * density, 21f * density, 20f * density, 22.8f * density)
                    cubicTo(23f * density, 23.8f * density, 25f * density, 23f * density, 25.5f * density, 20.8f * density)
                    lineTo(22f * density, 17.8f * density)
                    cubicTo(21.5f * density, 17.2f * density, 20.8f * density, 17f * density, 20.1f * density, 17.4f * density)
                    lineTo(17.7f * density, 18.8f * density)
                    cubicTo(14.9f * density, 17.3f * density, 12.6f * density, 15f * density, 11.2f * density, 12.2f * density)
                    lineTo(12.7f * density, 9.8f * density)
                    cubicTo(13.1f * density, 9.1f * density, 12.9f * density, 8.5f * density, 12.3f * density, 8f * density)
                    close()
                }
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun ReferenceCalendar(fg: Color, muted: Color) {
    val today = Calendar.getInstance()
    val es = Locale("es", "CO")

    val year = today.get(Calendar.YEAR)
    val monthIndex = today.get(Calendar.MONTH)

    val firstDay = Calendar.getInstance().apply {
        set(year, monthIndex, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Calendar.DAY_OF_WEEK: Sunday=1 ... Saturday=7.
    // Convert to Monday=0 ... Sunday=6.
    val firstColumn = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7

    val monthName = SimpleDateFormat("MMMM", es)
        .format(firstDay.time)
        .uppercase(es)

    val weekDays = listOf("L", "M", "X", "J", "V", "S", "D")

    // Each row is explicitly a 7-column grid. This prevents numbers from
    // sticking together or collapsing into a single column on narrow screens.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Vertical month name.
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .padding(top = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                monthName.forEach { letter ->
                    OutlinedText(
                        text = letter.toString(),
                        color = fg,
                        fontSize = 20.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.width(7.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Solo el año: el mes ya aparece en vertical al costado.
                OutlinedText(
                    text = year.toString(),
                    color = fg,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp)
                )

                // Weekday header: exactly seven equal cells.
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    weekDays.forEach { label ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedText(
                                text = label,
                                color = muted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                // Six complete calendar rows. Blank cells remain blank, so
                // every date stays under its correct weekday.
                val totalCells = 42
                for (cell in 0 until totalCells step 7) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                    ) {
                        for (column in 0..6) {
                            val cellIndex = cell + column
                            val dayNumber = cellIndex - firstColumn + 1
                            val valid = dayNumber in 1..daysInMonth
                            val isToday = valid &&
                                    dayNumber == today.get(Calendar.DAY_OF_MONTH)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .widthIn(min = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (valid) {
                                    OutlinedText(
                                        text = dayNumber.toString(),
                                        color = if (isToday) Color(0xFF35C7FF) else fg,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// Contenedor visual del scroll de aplicaciones. Está diseñado como una cavidad
// física: marco exterior, túnel interior, bordes luminosos y sombras profundas.
// No transforma ni reordena los elementos; el contenido sigue siendo el mismo
// LazyVerticalGrid de 2 columnas y mantiene los iconos en 50.dp.
@Composable
private fun ApplicationCavity(
    modifier: Modifier = Modifier,
    foreground: Color,
    muted: Color,
    content: @Composable BoxScope.() -> Unit
) {
    val outerShape = RoundedCornerShape(24.dp)
    val innerShape = RoundedCornerShape(19.dp)
    val tunnelShape = RoundedCornerShape(15.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 1.dp)
            .shadow(
                elevation = 16.dp,
                shape = outerShape,
                clip = false
            )
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.94f),
                        Color(0xFF111820).copy(alpha = 0.98f),
                        Color.Black.copy(alpha = 0.99f)
                    )
                ),
                shape = outerShape
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            foreground.copy(alpha = 0.88f),
                            foreground.copy(alpha = 0.28f),
                            foreground.copy(alpha = 0.72f),
                            foreground.copy(alpha = 0.18f)
                        )
                    )
                ),
                outerShape
            )
            .clip(outerShape)
    ) {
        // Primer reborde: crea el espesor de la carcasa.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF25313A).copy(alpha = 0.95f),
                            Color(0xFF05080B).copy(alpha = 0.98f),
                            Color(0xFF182229).copy(alpha = 0.92f)
                        )
                    ),
                    shape = innerShape
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                foreground.copy(alpha = 0.60f),
                                foreground.copy(alpha = 0.10f),
                                foreground.copy(alpha = 0.48f)
                            )
                        )
                    ),
                    innerShape
                )
                .clip(innerShape)
        ) {
            // Segundo reborde, más oscuro, para que el grid parezca hundido.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.98f),
                                Color(0xFF0B1014).copy(alpha = 0.99f),
                                Color.Black.copy(alpha = 0.98f)
                            )
                        ),
                        shape = tunnelShape
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    foreground.copy(alpha = 0.40f),
                                    foreground.copy(alpha = 0.06f),
                                    foreground.copy(alpha = 0.32f)
                                )
                            )
                        ),
                        tunnelShape
                    )
                    .clip(tunnelShape)
            ) {
                Box(Modifier.fillMaxSize()) {
                    // Scroll real: los iconos se mueven dentro de la cavidad,
                    // mientras el marco permanece fijo.
                    content()

                    // Bordes de profundidad: oscurecen suavemente las zonas
                    // superior e inferior y generan el efecto de túnel.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.76f),
                                        Color.Black.copy(alpha = 0f)
                                    )
                                )
                            )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0f),
                                        Color.Black.copy(alpha = 0.86f)
                                    )
                                )
                            )
                    )

                    // Brillo interior muy fino, como el borde LED de la referencia.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            foreground.copy(alpha = 0.24f),
                                            Color.Transparent,
                                            foreground.copy(alpha = 0.18f)
                                        )
                                    )
                                ),
                                tunnelShape
                            )
                    )
                }
            }
        }
    }
}

// Modifier that fades content near the top/bottom edges of a scrollable
// container using an offscreen alpha mask, so items smoothly disappear
// while scrolling instead of being clipped abruptly.
private fun Modifier.fadingEdges(edgeHeight: androidx.compose.ui.unit.Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = edgeHeight.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fadePx
            ),
            blendMode = BlendMode.DstIn
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fadePx,
                endY = size.height
            ),
            blendMode = BlendMode.DstIn
        )
    }

// Fine gradient rule used to visually separate the calendar, weather and
// apps sections without altering the panel width or their content.
@Composable
private fun SectionDivider(fg: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        fg.copy(alpha = 0f),
                        fg.copy(alpha = 0.28f),
                        fg.copy(alpha = 0f)
                    )
                )
            )
    )
}

private sealed interface WeatherState {
    data object Loading : WeatherState
    data object PermissionRequired : WeatherState
    data object Unavailable : WeatherState
    data class Ready(
        val temperature: Int,
        val description: String,
        val place: String
    ) : WeatherState
}

@Composable
private fun WeatherBlock(weather: WeatherState, fg: Color, muted: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (weather) {
            WeatherState.Loading -> {
                OutlinedText("☁ --°", color = fg, fontSize = 31.sp)
                OutlinedText("Consultando clima…", color = fg, fontSize = 13.sp)
                OutlinedText("⌖ Ubicando dispositivo", color = muted, fontSize = 12.sp)
            }
            WeatherState.PermissionRequired -> {
                OutlinedText("☁ --°", color = fg, fontSize = 31.sp)
                OutlinedText("Ubicación requerida", color = fg, fontSize = 13.sp)
                OutlinedText("⌖ Activa el permiso de ubicación", color = muted, fontSize = 12.sp)
            }
            WeatherState.Unavailable -> {
                OutlinedText("☁ --°", color = fg, fontSize = 31.sp)
                OutlinedText("Clima no disponible", color = fg, fontSize = 13.sp)
                OutlinedText("⌖ Sin conexión o ubicación", color = muted, fontSize = 12.sp)
            }
            is WeatherState.Ready -> {
                OutlinedText("☁ ${weather.temperature}°", color = fg, fontSize = 31.sp)
                OutlinedText(weather.description, color = fg, fontSize = 13.sp)
                OutlinedText("⌖ ${weather.place}", color = muted, fontSize = 12.sp)
            }
        }
    }
}

private suspend fun getCurrentLocation(context: Context): Location? = withContext(Dispatchers.IO) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val fine = androidx.core.content.ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!fine && !coarse) return@withContext null

    val providers = buildList {
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
    }

    providers.mapNotNull { provider ->
        try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
    }.maxByOrNull { it.time } ?: run {
        if (providers.isEmpty()) return@withContext null

        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val provider = providers.first()
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location)
                    try { manager.removeUpdates(this) } catch (_: Exception) {}
                }
            }
            try {
                manager.requestLocationUpdates(
                    provider, 0L, 0f, listener, Looper.getMainLooper()
                )
                continuation.invokeOnCancellation {
                    try { manager.removeUpdates(listener) } catch (_: Exception) {}
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}

private suspend fun fetchCurrentWeather(
    latitude: Double,
    longitude: Double,
    context: Context
): WeatherState.Ready = withContext(Dispatchers.IO) {
    val url = URL(
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code" +
            "&timezone=auto"
    )

    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10000
        readTimeout = 10000
    }

    try {
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val current = JSONObject(response).getJSONObject("current")
        val temperature = current.getDouble("temperature_2m").toInt()
        val code = current.getInt("weather_code")

        val geocoder = android.location.Geocoder(context, Locale.getDefault())
        val place = try {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.let { address ->
                    address.locality
                        ?: address.subAdminArea
                        ?: address.adminArea
                        ?: "Ubicación actual"
                } ?: "Ubicación actual"
        } catch (_: Exception) {
            "Ubicación actual"
        }

        WeatherState.Ready(
            temperature = temperature,
            description = weatherDescription(code),
            place = place
        )
    } finally {
        connection.disconnect()
    }
}

private fun weatherDescription(code: Int): String = when (code) {
    0 -> "Despejado"
    1, 2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Niebla"
    51, 53, 55 -> "Llovizna"
    56, 57 -> "Llovizna helada"
    61, 63, 65 -> "Lluvia"
    66, 67 -> "Lluvia helada"
    71, 73, 75, 77 -> "Nieve"
    80, 81, 82 -> "Chubascos"
    85, 86 -> "Chubascos de nieve"
    95 -> "Tormenta"
    96, 99 -> "Tormenta con granizo"
    else -> "Condición desconocida"
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AppIcon(
    app: LauncherApp,
    fg: Color,
    onLaunched: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showMenu by remember(app.packageName) { mutableStateOf(false) }

    fun openApp() {
        context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.let {
                context.startActivity(it)
                onLaunched?.invoke(app.packageName)
            }
    }

    fun openAppInfo() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Algunos paquetes pueden no exponer información de aplicación.
        }
    }

    fun uninstallApp() {
        val packageUri = Uri.parse("package:${app.packageName}")

        // Android 9+ exige REQUEST_DELETE_PACKAGES para solicitar una
        // desinstalación mediante el instalador del sistema.
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = packageUri
        }

        try {
            if (uninstallIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(uninstallIntent)
                return
            }

            // Compatibilidad con algunas ROM que exponen ACTION_DELETE
            // en lugar de ACTION_UNINSTALL_PACKAGE.
            val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                data = packageUri
            }

            if (deleteIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(deleteIntent)
                return
            }

            openAppInfo()
        } catch (_: Exception) {
            // Último respaldo: ficha de información de la aplicación.
            openAppInfo()
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { openApp() },
                    onLongClick = { showMenu = true }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = drawableToBitmap(app.icon).asImageBitmap(),
                    contentDescription = app.name,
                    modifier = Modifier.size(50.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            OutlinedText(
                text = app.name.take(12),
                color = fg,
                fontSize = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Abrir") },
                onClick = {
                    showMenu = false
                    openApp()
                }
            )
            DropdownMenuItem(
                text = { Text("Información de la aplicación") },
                onClick = {
                    showMenu = false
                    openAppInfo()
                }
            )
            DropdownMenuItem(
                text = { Text("Desinstalar") },
                onClick = {
                    showMenu = false
                    uninstallApp()
                }
            )
        }
    }
}

@Composable
private fun OutlinedText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    outlineColor: Color = if (color.luminance() > 0.45f) Color.Black.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.82f)
) {
    val offsets = listOf(
        (-1).dp to 0.dp, (1).dp to 0.dp, 0.dp to (-1).dp, 0.dp to 1.dp,
        (-1).dp to (-1).dp, (1).dp to (-1).dp, (-1).dp to 1.dp, (1).dp to 1.dp
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        offsets.forEach { (dx, dy) ->
            Text(
                text = text,
                color = outlineColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = fontWeight,
                textAlign = textAlign,
                letterSpacing = letterSpacing,
                maxLines = maxLines,
                softWrap = softWrap,
                modifier = Modifier.offset(dx, dy)
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            textAlign = textAlign,
            letterSpacing = letterSpacing,
            maxLines = maxLines,
            softWrap = softWrap
        )
    }
}

@Composable
private fun ChoiceButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val accent = Color(0xFF35C7FF)
    val bg = if (selected) accent else Color.White.copy(alpha = 0.06f)
    val fg = if (selected) Color(0xFF07131F) else Color.White.copy(alpha = 0.85f)
    val borderColor = if (selected) accent else Color.White.copy(alpha = 0.16f)

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = bg,
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp)
        )
    }
}

// Botón de selección de fondo predeterminado que muestra una miniatura real
// de la imagen, en vez de solo texto, para reconocer el fondo antes de
// aplicarlo.
@Composable
private fun PresetThumbnailButton(
    selected: Boolean,
    text: String,
    drawableRes: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(drawableRes) {
        android.graphics.BitmapFactory.decodeResource(context.resources, drawableRes)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(width = 84.dp, height = 60.dp)
                .shadow(elevation = if (selected) 6.dp else 0.dp, shape = RoundedCornerShape(12.dp), clip = false)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color(0xFF35C7FF) else Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = text,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (selected) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .background(Color(0xFF35C7FF), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✓",
                        color = Color(0xFF07131F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.025f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .background(SettingsAccent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    note: String? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Box(
                modifier = Modifier
                    .background(SettingsAccent.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    "${(value * 100).toInt()}%",
                    color = SettingsAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            Text(note, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, lineHeight = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = SettingsAccent,
                activeTrackColor = SettingsAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Transparente", color = Color.White.copy(alpha = 0.40f), fontSize = 10.sp)
            Text("Sólido", color = Color.White.copy(alpha = 0.40f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun SettingsSubheading(text: String, note: String? = null) {
    Column {
        Text(
            text.uppercase(Locale.getDefault()),
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            Text(note, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

private val SettingsAccent = Color(0xFF35C7FF)

@Composable
private fun SettingsPanel(
    style: SavedClockStyle,
    seconds: Boolean,
    bgMode: BackgroundMode,
    bgColor: String,
    bgImage: String?,
    tone: TextTone,
    leftPanelOpacity: Float,
    clockOpacity: Float,
    dayOpacity: Float,
    foreground: Color,
    onStyle: (SavedClockStyle) -> Unit,
    onSeconds: (Boolean) -> Unit,
    onBgMode: (BackgroundMode) -> Unit,
    onBgColor: (String) -> Unit,
    onPickImage: () -> Unit,
    onPresetBackground: (String) -> Unit,
    onTone: (TextTone) -> Unit,
    onLeftPanelOpacity: (Float) -> Unit,
    onClockOpacity: (Float) -> Unit,
    onDayOpacity: (Float) -> Unit,
    onDefaultLauncher: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF15161A), Color(0xFF0B0B0D))
                )
            )
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 28.dp)
    ) {
        // Cabecera: título + descripción + botón de cierre circular.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Configuración",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Personaliza el reloj, el fondo y los accesos",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color.White, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(22.dp))

        // --- Reloj ---
        SettingsSectionCard(
            title = "Reloj",
            subtitle = "Elige el estilo del reloj y los datos que muestra"
        ) {
            SettingsSubheading("Estilo")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton(
                    selected = style == SavedClockStyle.CLASSIC,
                    text = "Clásico",
                    onClick = { onStyle(SavedClockStyle.CLASSIC) }
                )
                ChoiceButton(
                    selected = style == SavedClockStyle.MINIMAL,
                    text = "Minimalista",
                    onClick = { onStyle(SavedClockStyle.MINIMAL) }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton(
                    selected = style == SavedClockStyle.DIDONE,
                    text = "Didone",
                    onClick = { onStyle(SavedClockStyle.DIDONE) }
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar segundos", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Actualiza el reloj cada segundo",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = seconds,
                    onCheckedChange = onSeconds,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SettingsAccent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.18f)
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // --- Transparencias ---
        SettingsSectionCard(
            title = "Transparencias",
            subtitle = "Ajusta la opacidad de cada panel de la pantalla principal"
        ) {
            SettingsSlider(
                title = "Panel de calendario y aplicaciones",
                value = leftPanelOpacity,
                onValueChange = onLeftPanelOpacity
            )
            Spacer(Modifier.height(18.dp))
            SettingsSlider(
                title = "Fondo del reloj",
                value = clockOpacity,
                onValueChange = onClockOpacity,
                note = "Disponible en Clásico y Minimalista"
            )
            Spacer(Modifier.height(18.dp))
            SettingsSlider(
                title = "Fondo del nombre del día",
                value = dayOpacity,
                onValueChange = onDayOpacity
            )
        }

        Spacer(Modifier.height(14.dp))

        // --- Color del texto ---
        SettingsSectionCard(
            title = "Color del texto",
            subtitle = "Aplica al reloj, calendario, clima y nombres de apps"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton(
                    selected = tone == TextTone.WHITE,
                    text = "Blanco",
                    onClick = { onTone(TextTone.WHITE) }
                )
                ChoiceButton(
                    selected = tone == TextTone.BLACK,
                    text = "Negro",
                    onClick = { onTone(TextTone.BLACK) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // --- Fondo de pantalla ---
        SettingsSectionCard(
            title = "Fondo de pantalla",
            subtitle = "Color sólido, una imagen propia o los diseños incluidos"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton(
                    selected = bgMode == BackgroundMode.SOLID,
                    text = "Color sólido",
                    onClick = { onBgMode(BackgroundMode.SOLID) }
                )
                ChoiceButton(
                    selected = bgMode == BackgroundMode.IMAGE && bgImage?.startsWith("preset:") != true,
                    text = "Imagen propia",
                    onClick = { onBgMode(BackgroundMode.IMAGE) }
                )
            }

            Spacer(Modifier.height(14.dp))

            if (bgMode == BackgroundMode.SOLID) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("#07131F", "#000000", "#102A43", "#18202A", "#24112F").forEach { hex ->
                        val isSelected = bgColor.equals(hex, ignoreCase = true)
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(50))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) SettingsAccent else Color.White.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { onBgColor(hex) }
                        )
                    }
                }
            } else {
                Button(
                    onClick = onPickImage,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SettingsAccent,
                        contentColor = Color(0xFF07131F)
                    )
                ) {
                    Text("Elegir imagen de la galería", fontWeight = FontWeight.Bold)
                }
            }

            // Fondos predeterminados: la selección disponible depende del
            // estilo de reloj activo, igual que en la versión anterior.
            if (style == SavedClockStyle.MINIMAL) {
                Spacer(Modifier.height(18.dp))
                SettingsSubheading("Fondos minimalistas", "Solo disponibles con el reloj minimalista")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresetThumbnailButton(
                        selected = bgImage == "preset:forest",
                        text = "Circuitos",
                        drawableRes = com.yao.R.drawable.bg_minimal_forest,
                        onClick = { onPresetBackground("preset:forest") }
                    )
                    PresetThumbnailButton(
                        selected = bgImage == "preset:bulb",
                        text = "Hexágonos",
                        drawableRes = com.yao.R.drawable.bg_minimal_bulb,
                        onClick = { onPresetBackground("preset:bulb") }
                    )
                    PresetThumbnailButton(
                        selected = bgImage == "preset:pulse",
                        text = "Geometría",
                        drawableRes = com.yao.R.drawable.bg_minimal_pulse,
                        onClick = { onPresetBackground("preset:pulse") }
                    )
                }
            }

            if (style == SavedClockStyle.CLASSIC) {
                Spacer(Modifier.height(18.dp))
                SettingsSubheading("Fondos clásicos", "Diseñados para la circunferencia del reloj y el panel izquierdo")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresetThumbnailButton(
                        selected = bgImage == "preset:lampara",
                        text = "Lámpara",
                        drawableRes = com.yao.R.drawable.lampara,
                        onClick = { onPresetBackground("preset:lampara") }
                    )
                    PresetThumbnailButton(
                        selected = bgImage == "preset:classic_spheres",
                        text = "Esferas",
                        drawableRes = com.yao.R.drawable.bg_classic_spheres,
                        onClick = { onPresetBackground("preset:classic_spheres") }
                    )
                    PresetThumbnailButton(
                        selected = bgImage == "preset:classic_veil",
                        text = "Velo",
                        drawableRes = com.yao.R.drawable.bg_classic_veil,
                        onClick = { onPresetBackground("preset:classic_veil") }
                    )
                }
            }

            if (style == SavedClockStyle.DIDONE) {
                Spacer(Modifier.height(18.dp))
                SettingsSubheading("Fondos Didone", "Solo disponibles con el reloj Didone")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresetThumbnailButton(
                        selected = bgImage == "preset:didone_blue",
                        text = "Azul",
                        drawableRes = com.yao.R.drawable.bg_didone_blue,
                        onClick = { onPresetBackground("preset:didone_blue") }
                    )
                    PresetThumbnailButton(
                        selected = bgImage == "preset:didone_purple",
                        text = "Violeta",
                        drawableRes = com.yao.R.drawable.bg_didone_purple,
                        onClick = { onPresetBackground("preset:didone_purple") }
                    )
                    PresetThumbnailButton(
                        selected = bgImage == "preset:didone_red",
                        text = "Rojo",
                        drawableRes = com.yao.R.drawable.bg_didone_red,
                        onClick = { onPresetBackground("preset:didone_red") }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // --- Sistema ---
        SettingsSectionCard(
            title = "Sistema",
            subtitle = "Usa este launcher como pantalla de inicio predeterminada"
        ) {
            Button(
                onClick = onDefaultLauncher,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SettingsAccent,
                    contentColor = Color(0xFF07131F)
                )
            ) {
                Text("Establecer como launcher predeterminado", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

private fun importExternalWallpaper(context: android.content.Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "wallpapers")
        if (!dir.exists()) dir.mkdirs()

        // Conservamos únicamente la última imagen recibida externamente.
        dir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }

        val target = File(dir, "shared_wallpaper.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null

        "local:${target.absolutePath}"
    } catch (_: Exception) {
        null
    }
}

/**
 * Página secundaria del launcher (índice 1 del HorizontalPager).
 * Se revela al deslizar la pantalla principal hacia la IZQUIERDA.
 * - Parte superior: aplicaciones usadas recientemente.
 * - Parte inferior: accesos rápidos personalizables (añadir / quitar).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAccessPage(
    apps: List<LauncherApp>,
    quickAccessPkgs: List<String>,
    recentPkgs: List<String>,
    foreground: Color,
    muted: Color,
    onRecordRecent: (String) -> Unit,
    onRemoveQuickAccess: (String) -> Unit,
    onRequestAdd: () -> Unit
) {
    val appMap = remember(apps) { apps.associateBy { it.packageName } }
    val recentApps = recentPkgs.mapNotNull { appMap[it] }
    val quickApps = quickAccessPkgs.mapNotNull { appMap[it] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        OutlinedText(
            text = "Recientes y accesos",
            color = foreground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            textAlign = TextAlign.Center
        )

        OutlinedText(
            text = "RECIENTES",
            color = foreground.copy(alpha = 0.90f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            textAlign = TextAlign.Center
        )

        if (recentApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(
                        Color.Black.copy(alpha = 0.22f),
                        RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, foreground.copy(alpha = 0.10f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                OutlinedText(
                    text = "Aún no hay aplicaciones recientes",
                    color = muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                lazyRowItems(recentApps, key = { it.packageName }) { app ->
                    QuickAccessAppIcon(
                        app = app,
                        fg = foreground,
                        onClick = {
                            onRecordRecent(app.packageName)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionDivider(foreground)

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedText(
                text = "ACCESOS RÁPIDOS",
                color = foreground.copy(alpha = 0.90f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )
            // Botón + para añadir una aplicación a la lista de accesos rápidos.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF35C7FF).copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF35C7FF), RoundedCornerShape(10.dp))
                    .clickable(onClick = onRequestAdd),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = Color(0xFF07131F),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (quickApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Color.Black.copy(alpha = 0.18f),
                        RoundedCornerShape(18.dp)
                    )
                    .border(1.dp, foreground.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedText(
                        text = "Sin accesos rápidos",
                        color = muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedText(
                        text = "Toca + para agregar aplicaciones",
                        color = muted.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .fadingEdges(20.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(quickApps, key = { it.packageName }) { app ->
                        QuickAccessAppIcon(
                            app = app,
                            fg = foreground,
                            showRemoveHint = true,
                            onClick = { onRecordRecent(app.packageName) },
                            onLongClick = { onRemoveQuickAccess(app.packageName) }
                        )
                    }
                }
            }
        }

        // Indicador sutil: desliza a la derecha para volver.
        Spacer(Modifier.height(8.dp))
        OutlinedText(
            text = "desliza para volver al inicio  →",
            color = muted.copy(alpha = 0.55f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAccessAppIcon(
    app: LauncherApp,
    fg: Color,
    showRemoveHint: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(72.dp)
            .combinedClickable(
                onClick = {
                    context.packageManager.getLaunchIntentForPackage(app.packageName)
                        ?.let {
                            context.startActivity(it)
                            onClick()
                        }
                },
                onLongClick = { onLongClick?.invoke() }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = drawableToBitmap(app.icon).asImageBitmap(),
                contentDescription = app.name,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedText(
            text = app.name.take(10),
            color = fg,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        if (showRemoveHint) {
            OutlinedText(
                text = "mantener para quitar",
                color = fg.copy(alpha = 0.40f),
                fontSize = 8.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AddQuickAccessDialog(
    apps: List<LauncherApp>,
    alreadyAdded: Set<String>,
    foreground: Color,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    val available = apps.filter { it.packageName !in alreadyAdded }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(120f)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.78f)
                .background(Color(0xFF0E1A28), RoundedCornerShape(20.dp))
                .border(1.dp, foreground.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedText(
                    text = "Agregar acceso rápido",
                    color = foreground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text("Cerrar", color = foreground)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (available.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedText(
                        text = "Todas las aplicaciones ya están agregadas",
                        color = foreground.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(available, key = { it.packageName }) { app ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(app.packageName) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = drawableToBitmap(app.icon).asImageBitmap(),
                                contentDescription = app.name,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedText(
                                text = app.name.take(12),
                                color = foreground,
                                fontSize = 10.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Abre la aplicación de cámara del sistema para capturas reales de fotos y video.
 * Preferimos INTENT_ACTION_STILL_IMAGE_CAMERA (abre la UI completa de la cámara,
 * desde la cual el usuario puede cambiar a modo video). Si no está disponible,
 * intentamos ACTION_IMAGE_CAPTURE y ACTION_VIDEO_CAPTURE como respaldo.
 */
private fun openRealCamera(context: android.content.Context) {
    val pm = context.packageManager
    val candidates = listOf(
        android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
        android.provider.MediaStore.INTENT_ACTION_VIDEO_CAMERA,
        android.provider.MediaStore.ACTION_IMAGE_CAPTURE,
        android.provider.MediaStore.ACTION_VIDEO_CAPTURE
    )
    for (action in candidates) {
        try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {
            // probar siguiente
        }
    }
    // Último recurso: buscar cualquier actividad de cámara instalada.
    try {
        val camIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val cameras = pm.queryIntentActivities(
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        if (cameras.isNotEmpty()) {
            val ai = cameras[0].activityInfo
            camIntent.setClassName(ai.packageName, ai.name)
            context.startActivity(camIntent)
        }
    } catch (_: Exception) {
        // Sin cámara disponible.
    }
}

private fun loadApps(pm: PackageManager): List<LauncherApp> {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map {
            LauncherApp(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                it.loadIcon(pm)
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.name.lowercase() }
}

private fun drawableToBitmap(d: Drawable): Bitmap {
    val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
    val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = ACanvas(b)
    d.setBounds(0, 0, c.width, c.height)
    d.draw(c)
    return b
}
