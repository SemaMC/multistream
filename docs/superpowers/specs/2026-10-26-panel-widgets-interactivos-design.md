# Spec — Panel interactivo de widgets (mover/redimensionar con dedos)

- Fecha: 2026-10-26
- Estado: diseño aprobado en chat (5/5 secciones), pendiente de revisión escrita
- Alcance: `:feature:settings` (editor) + `:core:widgets` (`WidgetLayoutManager`) + `:core:common` (sin cambios de forma)
- Enfoque: A — Compose directo (ruta arquitectural, brainstorming 2026-10-26)
- Restricción de ejecución: la implementación solo podrá usar subagentes con modelos free (motivo: costos). Decisión del usuario 2026-10-26.

## 1. Intento acordado (qué se construye y por qué)

**Resultado:** en la pantalla de configuración de widgets, cada layout (horizontal 16:9 / vertical 9:16)
se representa como un **Panel** — canvas fijo con aspect-ratio fijo — dentro del cual los **widgets**
se ven como cajas que se **arrastran con un dedo para mover** y se **estiran desde un handle en la
esquina inferior-derecha para redimensionar**. `position` + `size` de `Widget` dejan de editarse a
mano y pasan a editarse por gesto.

**Decisiones y motivos:**

1. Panel = contenedor fijo, widget = móvil (motivo: lo pidió el usuario; el Panel delimita el área
   del layout y el widget es lo que se coloca).
2. Edición solo en config, no en `StreamingScreen` (motivo: el usuario la excluyó; el futuro sistema
   de escenas traerá su propia edición en vivo y hoy sería duplicar).
3. Editor híbrido con modo editar — caja simple al arrastrar, WebView/vista previa al soltar o
   seleccionar (motivo: evita que el WebView robe los gestos táctiles).
4. Resize solo con handle de esquina, sin pinch (motivo: elección del usuario; precisión con un dedo).
5. Movimiento libre con aviso + snap a bordes y centro, desactivable (motivo: elección del usuario;
   libre no bloquea, el aviso informa del recorte, el snap acelera alineación).
6. Alta por "Panel directo": Add crea el widget centrado con tamaño por defecto y URL editable
   in-situ, sin dialog numérico X/Y/W/H (motivo: elección del usuario; el gesto sustituye a los números).
7. Hecho = "con validación": gesto persistente en memoria app + aviso/snap + tests (motivo: criterio
   de terminado elegido por el usuario).
8. Enfoque A, Compose directo (motivo: YAGNI — mínimo código nuevo, reutiliza `LayoutPreview` y los
   `StateFlow` existentes; `Panel` queda como precursor de `Escena = Panel + id + nombre`).

## 2. Estado actual (punto de partida verificado)

- `core/common/Widget.kt:3` — `Widget(id, url, position: WidgetPosition(x,y), size: WidgetSize(w,h), layout)`.
- `core/common/Layout.kt:3` — `HORIZONTAL` (1920×1080) / `VERTICAL` (1080×1920).
- `core/widgets/WidgetLayoutManager.kt:20` — singleton con `MutableStateFlow` + `addWidget`/`removeWidget`
  y flows por layout. Sin `updateWidget` (se añade en esta feature).
- `feature/settings/WidgetConfigScreen.kt` — lista por layout + `AddWidgetDialog` numérico (se reduce
  a pedir URL; el alta centra el widget).
- `feature/streaming-ui/LayoutPreview.kt:24` — preview estático (solo cuenta widgets). El `Panel`
  reutiliza su idea pero dibuja widgets reales; `StreamingScreen` sigue solo leyendo (sin edición).

## 3. Arquitectura y componentes (Sección 1, aprobada)

- `Panel(layout, widgets, onMove, onResize)` — nuevo `@Composable` en `:feature:settings`. `Box` con
  aspect-ratio fijo (16:9 / 9:16). Contenedor fijo, no se mueve ni se redimensiona.
- `WidgetBox(widget, selected, editMode)` — caja dentro del Panel. En arrastre: placeholder con
  URL + tamaño (sin WebView). Al soltar/seleccionar con toggle "Vista previa web" on: WebView real.
- `WidgetLayoutManager.updateWidget(id, position, size)` — nuevo método; el manager sigue siendo
  single source of truth. El estado transitorio del drag vive solo en Compose (`remember` local) y
  hace commit al soltar para no recomponer la lista por frame.
- `PanelMapper` (función pura nueva) — convierte px-táctiles del `Box` ↔ px-layout
  (1920×1080 / 1080×1920). `Widget` no cambia de forma: sigue guardando px de layout.
- `SnapHelper` (función pura nueva) — imán a bordes + centro H/V, tolerancia 12 px-layout,
  flag `snapEnabled` por panel. Devuelve posición corregida + guías a dibujar.
- Alta "Panel directo": `Widget(id=UUID, url, centro del layout, 480×270)`; la caja nace
  seleccionada con URL editable in-situ.

## 4. Gestos y reglas (Sección 2, aprobada)

- Mover: un dedo en el cuerpo (`detectDragGestures`); placeholder durante el gesto; commit al soltar.
- Redimensionar: handle ○ 24 dp en esquina inferior-derecha, visible solo en la seleccionada;
  arrastre diagonal cambia `width/height`; sin lock de aspecto (YAGNI).
- Libre con aviso: fuera parcial → borde amarillo discontinuo + ⚠ "fuera del panel (se recortará en
  el stream)"; fuera total → rojo "fuera del panel (no se verá)". Nunca bloquea; el compositor recorta.
- Snap desactivable: toggle "Imán" por panel (default on); al acercarse corrige y dibuja guías;
  con off, movimiento 1:1.
- Límites duros: mínimo 100×100 px-layout; sin máximo (coherente con "libre"). Tap fuera deselecciona.
  Sin reorden-Z manual (el orden de lista manda; lo necesitará escenas, hoy sobra).

## 5. Datos y persistencia (Sección 3, aprobada)

- Flujo: gesto → offset local Compose → `onDragEnd`/fin de handle → `PanelMapper` → `SnapHelper` →
  `ViewModel.move/resize` → `WidgetLayoutManager.updateWidget` → `StateFlow` por layout → redibuja
  Panel y lo lee el compositor. Sin doble fuente de verdad fuera del gesto.
- "Persistente" = memoria de app (singleton + ViewModel sobreviven a rotación/volver). Sin DataStore
  en esta feature (hoy no hay guardado en disco; escenas lo añadirá una sola vez para
  `Escena→Panel→Widgets`).
- URL vacía al deseleccionar → borde rojo "falta URL"; el compositor la ignora, no rompe el stream.
- Borrar: papelera actual + ✕ en caja seleccionada, ambas a `removeWidget`.

## 6. Errores y casos borde (Sección 4, aprobada)

| Caso | Comportamiento |
|---|---|
| URL vacía/inválida | Se guarda, borde rojo; compositor la ignora |
| Fuera total | Aviso rojo, se guarda (recuperable desde la lista) |
| WebView de preview falla | Cae a placeholder + reintentar; el arrastre no depende de la web |
| Rotación / 2.º dedo a mitad de drag | Cancela al último commit válido; clamp a mínimo, sin `NaN`/0 |
| Rendimiento | Solo la caja arrastrada recompone por frame; 6–8 widgets a 60 fps |

## 7. Testing y criterio de hecho (Sección 5, aprobada)

- Unit JVM: `SnapHelperTest` (bordes/centro, tolerancia, off), `PanelMapperTest` (ida/vuelta en ambos
  layouts, redondeo), extensión de `WidgetLayoutManagerTest` (`updateWidget`: mueve/resize, layout
  intacto, id inexistente = no-op).
- ViewModel: extender `WidgetConfigViewModelTest` (move/resize delegan, alta centrada 480×270, borrar).
- Validación manual (hecho sí o sí): mover con dedo, resize con handle, toggle imán on/off, aviso
  amarillo/rojo, rotar y volver mantiene todo, `StreamingScreen` refleja sin poder editar.
- Fuera de alcance: DataStore, pinch, rotación de widgets, z-order manual, edición en streaming.

## 8. Ficheros a tocar (implementación, no este spec)

- Añadir: `Panel.kt`, `WidgetBox.kt` en `:feature:settings`; `PanelMapper.kt`, `SnapHelper.kt`
  (+ tests) en `:core:widgets` (lógica pura, testeable en JVM sin Android).
- Modificar: `WidgetLayoutManager.kt` (+`updateWidget`), `WidgetConfigViewModel.kt`
  (+`move/resize`, alta centrada), `WidgetConfigScreen.kt` (Panel por layout, dialog solo-URL).
- No tocar: `StreamingScreen.kt` (solo lectura, como hoy), `Widget.kt` (sin cambios de forma).

## 9. Compatibilidad con escenas (futuro)

`Panel` se diseña para que mañana `Escena = Panel + id + nombre` sin romper `Widget` ni el manager.
Nada de esta feature presupone el modelo de escenas; solo deja el hueco.
