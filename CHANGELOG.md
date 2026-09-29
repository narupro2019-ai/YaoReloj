# Yao Reloj — historial de cambios

Este archivo reemplaza los múltiples `README_*.txt` y `CORRECCION_*.txt`
sueltos que acompañaban versiones anteriores del proyecto. Se conserva
un único documento con el resumen de cada cambio relevante.

## Renombrado del proyecto

- El proyecto pasa a llamarse **Yao Reloj** (antes "Custom Clock Launcher" /
  `CustomClockLauncherPro`).
- Paquete y `applicationId`: `com.example.customclocklauncher` → `com.yao`.
- Tema Compose `CustomClockLauncherTheme` → `YaoRelojTheme`; estilo XML
  `Theme.CustomClockLauncher` → `Theme.YaoReloj`.
- Se añadió `README.md`.
- Al cambiar el `applicationId`, Android la trata como una app distinta: no
  actualiza sobre la instalación anterior ni conserva sus preferencias.

## v11 — Interfaz de configuración rediseñada (esta versión)

- **Panel de Configuración completamente reorganizado**: pasa de una
  lista plana de textos y controles a tarjetas agrupadas por tema
  (Reloj, Transparencias, Color del texto, Fondo de pantalla, Sistema),
  cada una con título, descripción y una barra de acento de color.
- **Color de acento unificado** (`#35C7FF`, el mismo tono usado para
  resaltar el día actual en el calendario) aplicado a botones
  seleccionados, interruptores, sliders y miniaturas de fondo, para
  dar coherencia visual a toda la app.
- **Controles con mejor jerarquía**: subtítulos de sección, porcentaje
  de opacidad en una "píldora" junto al slider, interruptor de
  segundos con descripción corta, botones de acción con color de
  marca en vez de gris genérico.
- **Cabecera de Configuración** con título, subtítulo y botón de
  cierre circular (antes era un simple texto "Cerrar").
- **Botones de estilo (ChoiceButton)**: ahora tienen borde propio y
  estado seleccionado con el color de acento en vez de blanco/negro
  plano.
- **Miniaturas de fondo (PresetThumbnailButton)**: la marca de
  seleccionado pasa de un check centrado a una insignia circular en
  la esquina, con sombra sutil cuando está activa.
- **Página de Recientes y accesos rápidos**: se agregó un título de
  página para orientar al usuario, y el botón "+" de agregar acceso
  ahora usa el color de acento en vez de un gris neutro.
- Se eliminaron los README individuales de versiones anteriores y se
  reemplazaron por este changelog único.

No se modificó la lógica de negocio: apertura de apps, cámara,
teléfono, clima, calendario, desinstalación, HorizontalPager, guardado
de preferencias, etc. permanecen igual que antes.

## Historial previo (resumido)

- **Accesos rápidos del launcher**: se agregaron los botones de
  Cámara y Teléfono encima del botón de Configuración, en la esquina
  inferior derecha, con el mismo lenguaje visual de trazos.
- **Deslizar a la izquierda / Home / cámara real**: deslizar hacia la
  IZQUIERDA desde la pantalla principal abre la página de Recientes +
  Accesos rápidos (antes era hacia la derecha). El botón Home del
  sistema siempre regresa a la pantalla principal. La cámara abre la
  app completa del sistema (foto y video reales) en vez de solo un
  thumbnail.
- **Cambio de divisor nublado**: el efecto de desenfoque (solo en el
  reloj minimalista) termina exactamente en la línea divisora, sin
  invadir el área del reloj.
- **Cavidad del scroll de aplicaciones**: el área de aplicaciones
  ganó un marco 3D con profundidad, reborde interior, brillo
  perimetral y degradados, manteniendo el grid de 2 columnas original.
- **Contornos y bloqueo del menú de configuración**: se añadieron
  contornos de alto contraste a los textos sobre cualquier fondo, y el
  panel de Configuración pasó a comportarse como una capa modal que
  bloquea toques accidentales sobre los iconos debajo.
- **Cristal empañado**: el efecto de vidrio esmerilado del panel
  izquierdo (solo con reloj minimalista/Didone) se corrigió para usar
  un recorte exacto del fondo real, evitando el efecto de "imagen
  duplicada".
- **Desinstalación de apps**: se añadió el permiso
  `REQUEST_DELETE_PACKAGES` y una cadena de intents de respaldo
  (`ACTION_UNINSTALL_PACKAGE` → `ACTION_DELETE` → información de la
  app) para garantizar que la desinstalación funcione en todos los
  dispositivos.
- **Pantalla de accesos rápidos y recientes**: nueva segunda página
  del launcher con lista de apps recientes y grid de accesos rápidos
  personalizables (agregar con "+", quitar con pulsación larga).
- **Correcciones de compilación**: error de `cubicTo()` con
  coordenadas incompletas en el icono de teléfono, y error de
  `Modifier.weight()` usado fuera de un `ColumnScope`.
