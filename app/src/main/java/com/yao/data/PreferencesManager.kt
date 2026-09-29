package com.yao.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "launcher_preferences")

enum class SavedClockStyle { CLASSIC, MINIMAL, DIDONE }
enum class BackgroundMode { SOLID, IMAGE }
enum class TextTone { WHITE, BLACK }

class PreferencesManager(private val context: Context) {
    private val styleKey = stringPreferencesKey("clock_style")
    private val secondsKey = booleanPreferencesKey("show_seconds")
    private val bgModeKey = stringPreferencesKey("background_mode")
    private val bgColorKey = stringPreferencesKey("background_color")
    private val bgImageKey = stringPreferencesKey("background_image")
    private val textToneKey = stringPreferencesKey("text_tone")
    private val leftPanelOpacityKey = floatPreferencesKey("left_panel_opacity")
    private val clockOpacityKey = floatPreferencesKey("clock_opacity")
    private val dayOpacityKey = floatPreferencesKey("day_opacity")
    // Paquetes de accesos rápidos guardados por el usuario (separados por coma).
    private val quickAccessKey = stringPreferencesKey("quick_access_packages")
    // Paquetes usados recientemente (más reciente primero, separados por coma).
    private val recentAppsKey = stringPreferencesKey("recent_apps_packages")

    val clockStyle: Flow<SavedClockStyle> = context.dataStore.data.map {
        when (it[styleKey]) {
            SavedClockStyle.MINIMAL.name -> SavedClockStyle.MINIMAL
            SavedClockStyle.DIDONE.name -> SavedClockStyle.DIDONE
            else -> SavedClockStyle.CLASSIC
        }
    }

    val showSeconds: Flow<Boolean> = context.dataStore.data.map { it[secondsKey] ?: true }

    val backgroundMode: Flow<BackgroundMode> = context.dataStore.data.map {
        if (it[bgModeKey] == BackgroundMode.IMAGE.name) BackgroundMode.IMAGE
        else BackgroundMode.SOLID
    }

    val backgroundColor: Flow<String> = context.dataStore.data.map {
        it[bgColorKey] ?: "#07131F"
    }

    val backgroundImage: Flow<String?> = context.dataStore.data.map {
        it[bgImageKey]
    }

    val textTone: Flow<TextTone> = context.dataStore.data.map {
        if (it[textToneKey] == TextTone.BLACK.name) TextTone.BLACK
        else TextTone.WHITE
    }

    // Opacidad del fondo del área izquierda: 0f = totalmente transparente,
    // 1f = totalmente sólido. Se guarda para conservar la elección del usuario.
    val leftPanelOpacity: Flow<Float> = context.dataStore.data.map {
        (it[leftPanelOpacityKey] ?: 0f).coerceIn(0f, 1f)
    }

    // Opacidad independiente del fondo del reloj y del área del día.
    // 0f = totalmente transparente, 1f = totalmente sólido.
    val clockOpacity: Flow<Float> = context.dataStore.data.map {
        (it[clockOpacityKey] ?: 0f).coerceIn(0f, 1f)
    }

    val dayOpacity: Flow<Float> = context.dataStore.data.map {
        (it[dayOpacityKey] ?: 0f).coerceIn(0f, 1f)
    }

    val quickAccessPackages: Flow<List<String>> = context.dataStore.data.map {
        parsePackageList(it[quickAccessKey])
    }

    val recentPackages: Flow<List<String>> = context.dataStore.data.map {
        // Máximo 6 recientes; si había más guardados, se recortan.
        parsePackageList(it[recentAppsKey]).take(6)
    }

    suspend fun setClockStyle(v: SavedClockStyle) {
        context.dataStore.edit { it[styleKey] = v.name }
    }

    suspend fun setShowSeconds(v: Boolean) {
        context.dataStore.edit { it[secondsKey] = v }
    }

    suspend fun setBackgroundMode(v: BackgroundMode) {
        context.dataStore.edit { it[bgModeKey] = v.name }
    }

    suspend fun setBackgroundColor(v: String) {
        context.dataStore.edit { it[bgColorKey] = v }
    }

    suspend fun setBackgroundImage(v: String) {
        context.dataStore.edit { it[bgImageKey] = v }
    }

    suspend fun setTextTone(v: TextTone) {
        context.dataStore.edit { it[textToneKey] = v.name }
    }

    suspend fun setLeftPanelOpacity(v: Float) {
        context.dataStore.edit { it[leftPanelOpacityKey] = v.coerceIn(0f, 1f) }
    }

    suspend fun setClockOpacity(v: Float) {
        context.dataStore.edit { it[clockOpacityKey] = v.coerceIn(0f, 1f) }
    }

    suspend fun setDayOpacity(v: Float) {
        context.dataStore.edit { it[dayOpacityKey] = v.coerceIn(0f, 1f) }
    }

    suspend fun setQuickAccessPackages(packages: List<String>) {
        context.dataStore.edit {
            it[quickAccessKey] = packages.distinct().joinToString(",")
        }
    }

    suspend fun addQuickAccess(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = parsePackageList(prefs[quickAccessKey]).toMutableList()
            if (!current.contains(packageName)) {
                current.add(packageName)
                prefs[quickAccessKey] = current.joinToString(",")
            }
        }
    }

    suspend fun removeQuickAccess(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = parsePackageList(prefs[quickAccessKey]).toMutableList()
            current.remove(packageName)
            prefs[quickAccessKey] = current.joinToString(",")
        }
    }

    /**
     * Registra una aplicación como usada recientemente.
     * La mueve al frente; si ya hay 6, desaparece la más antigua
     * (la 6.ª sale, la 5.ª pasa a 6.ª, etc.).
     */
    suspend fun recordRecentApp(packageName: String, maxRecent: Int = 6) {
        context.dataStore.edit { prefs ->
            val current = parsePackageList(prefs[recentAppsKey]).toMutableList()
            current.remove(packageName)
            current.add(0, packageName)
            prefs[recentAppsKey] = current.take(maxRecent).joinToString(",")
        }
    }

    private fun parsePackageList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
