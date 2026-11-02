# Rediseño GUI v2 — design spec (2026-11-02)

Estado: aprobado por el usuario en brainstorming (ronda 2, sobre tablet física con la v1 instalada).

## Alcance
Corrige el bug de navegación de Ajustes en tablet y compacta Go Live + editor de widgets. Sin tocar `:core:*`, `:data:platforms` ni contratos de ViewModels.
No entra: nuevos backends de configuración, i18n, cambios de encoding/streaming/audio.

## Decisiones y motivos
1. Estructura A: solo Go Live (inicial) + Configuración. Motivo: el usuario la eligió explícitamente; Home se fusiona en Go Live y desaparece el hub de secciones como páginas.
2. Two-pane real en Configuración: izquierda opciones (Plataformas, Widgets), derecha editor embebido con la opción marcada. Motivo: el usuario lo exigió ("a la izquierda las opciones, a la derecha el menú"); elimina las rutas `settings/platforms` y `settings/widgets` como páginas aparte, que era la causa raíz del bug (desmontaban el panel y huérfana la selección, informe explore ses_e64d833f2ffeL5nOkW8lbscPFr).
3. Sin botón "Open configuration" ni placeholder intermedio: tocar opción muestra su editor directamente. Motivo: era el segundo tap que confundía ("me manda a otra página").
4. Previews y controles compactos (vertical sobre todo) + controles flotantes sencillos: las previews compactas incluyen la horizontal y la vertical del editor Y la vertical de Go Live, y los controles son flotantes y sencillos (los toggles, como chips), todo visible en una pantalla. Motivo: pedido explícito; hoy exigen scroll largo.
5. Barra inferior de estado en Go Live: fila por plataforma con dot verde (lista) / rojo (pendiente) + nombre, desde `HomeViewModel.platforms`. Motivo: pedido explícito; sin tocar ViewModels.
6. Acceso directo a edición de widgets + botón tuerca a Configuración desde Go Live; flecha ← en Configuración vuelve a Go Live. Motivo: pedidos explícitos.

## Componentes y rutas
- Reutilizar `PlatformRow`, `StatusChip`, `GoLiveButton`, `SectionHeader`, `SettingRow` (+ parámetro `selected`, que hoy no existe y es la mitad del bug) de `:feature:uikit`; los composables de plataformas/widgets se embeben en el pane derecho, no se duplican.
- Rutas: `go-live` (inicial) y `configuracion`; se eliminan como páginas `settings`, `settings/platforms`, `settings/widgets` (el rail/nav pasa a 2 items).

## Reglas transversales
- Insets: sin `safeDrawingPadding` en scaffolds padre; cada pantalla su padding; `adjustResize` se mantiene.
- Estado con `rememberSaveable`; prohibido `android:configChanges`. 48dp, `contentDescription`, sin color como único estado, contraste AA.

## Verificación del cambio
`.\gradlew.bat :feature:settings:testDebugUnitTest :feature:streaming-ui:testDebugUnitTest :feature:uikit:testDebugUnitTest` + `.\gradlew.bat :app:assembleDebug` (PowerShell, sin `&&`), más captura real del tablet por adb (`screencap`) revisada por el usuario. El repo sigue sin `AGENTS.md`.
