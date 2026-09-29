package com.yao

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.yao.ui.LauncherScreen
import com.yao.ui.theme.YaoRelojTheme

/**
 * Petición de imagen recibida desde otra aplicación (Galería, Zedge, etc.).
 * El token permite procesar dos veces la misma URI si el usuario la vuelve a compartir.
 */
data class ExternalWallpaperRequest(val uri: Uri, val token: Long)

class MainActivity : ComponentActivity() {
    private var externalWallpaperRequest by mutableStateOf<ExternalWallpaperRequest?>(null)
    /** Se incrementa cada vez que el sistema envía el intent HOME (botón inicio). */
    private var homePressToken by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        handleWallpaperIntent(intent)
        handleHomeIntent(intent)

        setContent {
            YaoRelojTheme {
                Surface(Modifier.fillMaxSize()) {
                    LauncherScreen(
                        externalWallpaperRequest = externalWallpaperRequest,
                        homePressToken = homePressToken,
                        onRequestDefaultLauncher = { requestDefaultLauncher() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWallpaperIntent(intent)
        handleHomeIntent(intent)
    }

    /**
     * Cuando el usuario pulsa el botón Home del sistema, el launcher recibe
     * un intent con CATEGORY_HOME. Usamos ese evento para volver siempre a
     * la pantalla principal (página 0 del pager).
     */
    private fun handleHomeIntent(intent: Intent) {
        val categories = intent.categories
        val isHome = (categories != null && categories.contains(Intent.CATEGORY_HOME)) ||
            (intent.action == Intent.ACTION_MAIN && categories != null && categories.contains(Intent.CATEGORY_HOME))
        // También tratamos ACTION_MAIN sin extras de wallpaper como re-entrada al home.
        if (isHome || (intent.action == Intent.ACTION_MAIN && extractImageUri(intent) == null)) {
            homePressToken = System.nanoTime()
        }
    }

    private fun handleWallpaperIntent(intent: Intent) {
        val uri = extractImageUri(intent) ?: return
        externalWallpaperRequest = ExternalWallpaperRequest(uri, System.nanoTime())
    }

    @Suppress("DEPRECATION")
    private fun extractImageUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_VIEW, Intent.ACTION_ATTACH_DATA -> {
                intent.data
                    ?: intent.clipData?.getItemAt(0)?.uri
                    ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list?.firstOrNull()
            }
            else -> null
        }
    }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val role = getSystemService(RoleManager::class.java)
            if (role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)) {
                startActivityForResult(
                    role.createRequestRoleIntent(RoleManager.ROLE_HOME),
                    1001
                )
                return
            }
        }
        try {
            startActivity(Intent("android.settings.HOME_SETTINGS"))
        } catch (_: Exception) {
            startActivity(Intent("android.settings.SETTINGS"))
        }
    }
}
