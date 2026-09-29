# Yao Reloj

Launcher de Android centrado en un reloj personalizable, con clima real,
calendario, accesos rápidos y fondos configurables. Hecho con Kotlin y
Jetpack Compose.

- **Paquete / applicationId:** `com.yao`
- **Nombre del proyecto Gradle:** `YaoReloj`
- **Versión:** 2.0 (`versionCode` 2)
- **SDK:** minSdk 26 · targetSdk / compileSdk 35

## Características

- **Tres estilos de reloj:** Minimal, Clásico y Didone (tipografía Playfair Display).
- **Fondos:** presets incluidos para cada estilo y fondos propios recibidos desde
  Galería, Zedge u otras apps (compartir, abrir con o "usar como").
- **Panel de configuración** en tarjetas: reloj, transparencias, color del texto,
  fondo de pantalla y sistema, con color de acento unificado.
- **Clima real** según la ubicación del dispositivo.
- **Calendario** con el día actual resaltado.
- **Lista de aplicaciones** en grid de 2 columnas, con desinstalación desde el launcher.
- **Segunda página** (deslizar a la izquierda): apps recientes y accesos rápidos
  personalizables (agregar con "+", quitar con pulsación larga).
- **Accesos directos** a Cámara y Teléfono.
- **Widget de reloj** para la pantalla de inicio.
- Se puede establecer como launcher predeterminado (categoría HOME).

## Permisos

| Permiso | Uso |
|---|---|
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | Obtener la ubicación para el clima |
| `INTERNET` | Consultar el clima |
| `REQUEST_DELETE_PACKAGES` | Desinstalar apps a petición del usuario |

## Compilar

Requisitos: Android Studio reciente, JDK 17.

1. Abre la carpeta `YaoReloj` en Android Studio y espera la sincronización de Gradle.
2. Ejecuta la configuración `app` en un dispositivo o emulador (Android 8.0+).
3. Al pulsar Home, elige **Yao Reloj** como launcher predeterminado.

Por línea de comandos: `./gradlew assembleDebug`

> Este zip no incluye el wrapper de Gradle (`gradlew`); Android Studio lo genera
> al abrir el proyecto, o puedes usar una instalación local de Gradle.

## Estructura

```
YaoReloj/
├── build.gradle.kts
├── settings.gradle.kts
└── app/src/main/
    ├── AndroidManifest.xml
    ├── java/com/yao/
    │   ├── MainActivity.kt
    │   ├── clock/          # MinimalClock, ClassicClock, DidoneClock
    │   ├── data/           # PreferencesManager (DataStore)
    │   ├── ui/             # LauncherScreen y tema (YaoRelojTheme)
    │   └── widget/         # ClockWidgetReceiver
    └── res/                # fuentes, fondos, iconos, layout del widget
```

## Tecnologías

Kotlin 2.0.21 · Android Gradle Plugin 8.7.3 · Compose BOM 2025.01.00 ·
Material 3 · DataStore Preferences.

## Historial

Consulta [CHANGELOG.md](CHANGELOG.md).
