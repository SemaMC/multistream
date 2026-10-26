# Panel interactivo de widgets — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En la configuración de widgets, cada layout se edita como un Panel táctil: arrastrar un widget lo mueve, tirar del handle de su esquina inferior-derecha lo redimensiona; snap y avisos según spec.

**Architecture:** Compose directo (enfoque A): `Panel`/`WidgetBox` en `:feature:settings` con `pointerInput`; lógica pura (`PanelMapper`, `SnapHelper`) y `updateWidget` en `:core:widgets`; `WidgetLayoutManager` sigue siendo la única fuente de verdad y el commit ocurre al soltar el dedo. `StreamingScreen` no cambia.

**Tech Stack:** Kotlin 2.x, Jetpack Compose + Material 3, coroutines StateFlow, Hilt, JUnit4 + MockK (ya en los módulos).

**Spec:** `docs/superpowers/specs/2026-10-26-panel-widgets-interactivos-design.md` (leer ambos; este plan implementa ese spec).

## Global Constraints

- `Widget` no cambia de forma: sigue guardando `position`/`size` en px de layout (`core/common/Widget.kt:3`).
- Resoluciones de layout: HORIZONTAL 1920×1080, VERTICAL 1080×1920 (`core/common/Layout.kt`).
- Tolerancia de snap: 12 px-layout. Mínimo de widget: 100×100 px-layout. Tamaño por defecto al crear: 480×270, centrado.
- Avisos: fuera parcial → amarillo discontinuo + ⚠; fuera total → rojo; nunca bloquean el guardado.
- Snap desactivable por panel (toggle "Imán", default on). Resize solo por handle ○ 24 dp en esquina inferior-derecha; sin pinch ni lock de aspecto.
- Edición solo en `WidgetConfigScreen`; `StreamingScreen.kt` no se modifica.
- Sin DataStore ni dependencias nuevas de terceros.
- Builds y tests SIEMPRE via `gradle.ps1` (fija JAVA_HOME al JDK 17 de Adoptium); PowerShell 5.1: no usar `&&`.
- Subagentes de ejecución: solo modelos free (restricción del spec, por costos).

## Review Focus

Los cinco fallos más probables que ningún test unitario de UI ejercita (cada línea se pinnea con el test de su tarea):

1. Gesto interrumpido (rotación/2.º dedo) a mitad de drag → debe quedar el último commit válido, sin `NaN` ni tamaño < mínimo. Test: Task 1 (`clampSize`) + validación manual (Task 6, paso M4).
2. Widget totalmente fuera del panel → borde rojo, se guarda igual y sigue arrastrable. Test: Task 2 (`SnapHelper` no altera posiciones libres) + Task 6 (paso M2, aviso rojo + recuperar desde la lista).
3. `updateWidget` con id inexistente → no-op sin excepción y flows intactos. Test: Task 3.
4. Toggle "Imán" off → movimiento 1:1 sin guías. Test: Task 2 (test `snap off`) + Task 6 (paso M3).
5. Alta con URL vacía → widget guardado, marcado "falta URL", ignorado por el compositor sin romper el stream. Test: Task 4 (`addUrlVacia guarda`) + Task 6 (paso M5).

---

### Task 1: `PanelMapper` (conversiones panel↔layout)

**Files:**
- Create: `core/widgets/src/main/kotlin/com/multistream/core/widgets/PanelMapper.kt`
- Test: `core/widgets/src/test/kotlin/com/multistream/core/widgets/PanelMapperTest.kt`

**Interfaces:**
- Consumes: `Layout`, `WidgetPosition`, `WidgetSize` de `:core:common`.
- Produces (Tasks 2, 5, 6 usan esto):
  - `data class PanelDims(val width: Int, val height: Int)` — px táctiles del Box en pantalla.
  - `object PanelMapper`:
    - `fun layoutSizeFor(layout: Layout): WidgetSize` — HORIZONTAL→1920×1080, VERTICAL→1080×1920.
    - `fun toLayoutPosition(touchX: Float, touchY: Float, panel: PanelDims, layout: Layout): WidgetPosition` — proporción (touchX/panel.width)*layoutW; redondeo a Float, sin clamp (lo libre es válido).
    - `fun toTouchPosition(position: WidgetPosition, panel: PanelDims, layout: Layout): WidgetPosition` — inversa exacta de `toLayoutPosition`.
    - `fun toLayoutSize(touchWidth: Float, touchHeight: Float, panel: PanelDims, layout: Layout): WidgetSize` — redondeo al px-layout más próximo (solo punto de redondeo del mapper; la UI trabaja en Float, sin redondeo de vuelta, para evitar deriva en ida-vuelta).
    - `fun clampSize(width: Int, height: Int): WidgetSize` — fuerza `>= 100` en ambos ejes.
    - `const val MIN_SIZE = 100`

- [ ] **Step 1: Write the failing test** — `PanelMapperTest` con estos casos exactos:
  - `layoutSizeFor devuelve 1920x1080 para HORIZONTAL y 1080x1920 para VERTICAL`
  - `toLayoutPosition es proporcional en ambos layouts` (H: touch 480,270 en panel 480,270 → 1920,1080; V: touch 270,480 en panel 540,960 → 540,960)
  - `toLayoutPosition y toTouchPosition son inversas exactas` (ida y vuelta sin pérdida en ambos layouts; la inversa no redondea, solo el sentido hacia layout)
  - `toLayoutSize redondea al px-layout mas proximo` (touch 120,6 en panel 480 H → 480,24)
  - `clampSize fuerza minimo 100x100` (clampSize(50, 0) == 100x100; clampSize(300, 400) == 300x400)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradle.ps1 :core:widgets:testDebugUnitTest --tests "com.multistream.core.widgets.PanelMapperTest"`
Expected: FAIL (`PanelMapper` no definido / compilación falla)

- [ ] **Step 3: Implement** `PanelMapper.kt` con las firmas exactas de arriba (puro, sin dependencias de Compose; aritmética proporcional + `roundToInt` solo en el sentido hacia layout).

- [ ] **Step 4: Run test to verify it passes** — mismo comando. Expected: PASS (5/5).

- [ ] **Step 5: Commit**

```powershell
git add core/widgets/src/main/kotlin/com/multistream/core/widgets/PanelMapper.kt core/widgets/src/test/kotlin/com/multistream/core/widgets/PanelMapperTest.kt
git commit -m "feat(widgets): PanelMapper conversions panel<->layout"
```

### Task 2: `SnapHelper` (imán a bordes/centro, desactivable)

**Files:**
- Create: `core/widgets/src/main/kotlin/com/multistream/core/widgets/SnapHelper.kt`
- Test: `core/widgets/src/test/kotlin/com/multistream/core/widgets/SnapHelperTest.kt`

**Interfaces:**
- Consumes: `Layout`, `WidgetPosition`, `WidgetSize`, `PanelMapper.layoutSizeFor`.
- Produces (Tasks 5, 6):
  - `enum class Guideline { LEFT_EDGE, RIGHT_EDGE, H_CENTER, TOP_EDGE, BOTTOM_EDGE, V_CENTER }`
  - `data class SnapResult(val position: WidgetPosition, val guides: Set<Guideline>)`
  - `object SnapHelper { const val TOLERANCE = 12f; fun snap(position: WidgetPosition, size: WidgetSize, layout: Layout, enabled: Boolean): SnapResult }`
  - Semántica: evalúa las 6 guías (bordes izq/der/arriba/abajo del widget vs bordes del panel; CENTRO del widget vs centro H/V del panel). Si |distancia| <= 12 px-layout, corrige la posición a la guía: en bordes `pos = borde panel (widget entra)`, en centro `pos = centroPanel − size/2`; añade la guía al set. Un widget ya alineado a una guía NO se mueve (solo añade la guía). Con `enabled=false` devuelve `position` sin tocar y set vacío. Fuera de tolerancia, sin cambio.

- [ ] **Step 1: Write the failing test** — `SnapHelperTest`:
  - `snap alinea al borde izquierdo dentro de tolerancia` (H: pos 4,0 size 100 → pos 0,0, guía LEFT_EDGE)
  - `snap alinea al centro horizontal del panel` (H: pos 950,0 size 10 → pos 955,0, guía H_CENTER; centro del widget 955 → 960, delta 5)
  - `snap alinea borde derecho y fondo` (H: pos 1826,976 size 100x100 → 1820,980, guías RIGHT_EDGE+BOTTOM_EDGE)
  - `fuera de tolerancia no cambia nada` (H: pos 50,50 size 100 → igual, set vacío)
  - `snap off devuelve la posicion intacta y sin guias` (H: pos 4,4 size 100 enabled=false → 4,4, vacío)
  - `widget ya centrado no se mueve pero declara las guias` (V: pos 532,955 size 16x10 → 532,955 sin mover, guías H_CENTER+V_CENTER)

- [ ] **Step 2: Run test to verify it fails** — mismo comando con `SnapHelperTest`. Expected: FAIL.

- [ ] **Step 3: Implement** `SnapHelper.kt` con la semántica de arriba (puro; usa `PanelMapper.layoutSizeFor` para las medidas del panel).

- [ ] **Step 4: Run test to verify it passes**. Expected: PASS (6/6).

- [ ] **Step 5: Commit**

```powershell
git add core/widgets/src/main/kotlin/com/multistream/core/widgets/SnapHelper.kt core/widgets/src/test/kotlin/com/multistream/core/widgets/SnapHelperTest.kt
git commit -m "feat(widgets): SnapHelper edge/center snap with disable flag"
```

### Task 3: `WidgetLayoutManager.updateWidget`

**Files:**
- Modify: `core/widgets/src/main/kotlin/com/multistream/core/widgets/WidgetLayoutManager.kt` (añadir método tras `removeWidget`)
- Test: `core/widgets/src/test/kotlin/com/multistream/core/widgets/WidgetLayoutManagerTest.kt` (añadir casos)

**Interfaces:**
- Consumes: nada nuevo.
- Produces (Task 4): `fun updateWidget(widgetId: String, position: WidgetPosition? = null, size: WidgetSize? = null, url: String? = null)` — `null` = "no cambia"; reemplaza el widget coincidente por copia con los nuevos valores (incluida url) y refresca los flows por layout; id inexistente = no-op. Parámetros `null`ables porque mover, redimensionar y renombrar URL ocurren en gestos separados.

- [ ] **Step 1: Write the failing test** — añadir a `WidgetLayoutManagerTest`:
  - `updateWidget cambia solo posicion cuando lo demas es null` (widget "1" H → updateWidget("1", WidgetPosition(50f,60f)) → pos 50,60 y size/url intactos; V intacto)
  - `updateWidget cambia solo size cuando lo demas es null`
  - `updateWidget cambia solo url cuando lo demas es null` (updateWidget("1", url = "https://nueva") → url nueva, pos/size intactos)
  - `updateWidget con id inexistente es un no-op` (flujo igual a antes, sin excepción)

- [ ] **Step 2: Run test to verify it fails** — `./gradle.ps1 :core:widgets:testDebugUnitTest --tests "com.multistream.core.widgets.WidgetLayoutManagerTest"`. Expected: FAIL (método no existe).

- [ ] **Step 3: Implement** el método en `WidgetLayoutManager` siguiendo el patrón existente (`widgets.update { ... }` + `refreshLayouts()`).

- [ ] **Step 4: Run test to verify it passes**. Expected: PASS (8/8 del fichero, sin romper los 4 previos).

- [ ] **Step 5: Commit**

```powershell
git add core/widgets/src/main/kotlin/com/multistream/core/widgets/WidgetLayoutManager.kt core/widgets/src/test/kotlin/com/multistream/core/widgets/WidgetLayoutManagerTest.kt
git commit -m "feat(widgets): WidgetLayoutManager.updateWidget"
```

### Task 4: `WidgetConfigViewModel` — move/resize y alta centrada

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/multistream/feature/settings/WidgetConfigViewModel.kt`
- Test: `feature/settings/src/test/kotlin/com/multistream/feature/settings/WidgetConfigViewModelTest.kt` (añadir casos)

**Interfaces:**
- Consumes: `WidgetLayoutManager.updateWidget` (Task 3, firma definitiva de 4 parámetros), `PanelMapper.clampSize`/`MIN_SIZE` (Task 1), `SnapHelper` (Task 2).
- Produces (Task 6):
  - `fun addWidget(layout: Layout, url: String = "")` — nace centrado en su layout con tamaño 480×270 (nueva sobrecarga; la antigua `addWidget(layout, url, x, y, w, h)` NO se elimina aquí: se marca `@Deprecated` delegando en la nueva, y Task 6 la elimina junto con su uso en pantalla y tests).
  - `fun moveWidget(id: String, x: Float, y: Float, snapEnabled: Boolean)` — `x,y` llegan YA en px-layout (Panel convierte antes de emitir, Task 5); busca el widget por id en `getAllWidgets().value` para obtener layout y size actuales (id inexistente = no-op), aplica `SnapHelper.snap(pos, size, layout, snapEnabled).position` y delega en `updateWidget(id, pos)`.
  - `fun resizeWidget(id: String, width: Int, height: Int)` — `width,height` en px-layout (Panel convierte); busca el widget por id (no-op si falta), aplica `PanelMapper.clampSize` y delega en `updateWidget(id, null, size)`.
  - `fun setWidgetUrl(id: String, url: String)` — delega en `updateWidget(id, url = url)`.

- [ ] **Step 1: Write the failing test** — añadir a `WidgetConfigViewModelTest` (usa manager real, patrón existente):
  - `moveWidget aplica snap y actualiza posicion` (H, widget 100x100 en 4,0; moveWidget(id, 4f, 0f, snapEnabled=true) → 0,0)
  - `moveWidget con snap off respeta el valor dado` (→ 4,0)
  - `moveWidget con id inexistente es no-op`
  - `resizeWidget clampa al minimo` (resizeWidget(id, 50, 0) → 100x100)
  - `resizeWidget sin tocar posicion`
  - `addWidget centrado con 480x270 y url vacia por defecto` (H → pos (720, 405), size 480x270, url "")
  - `setWidgetUrl actualiza la url sin tocar position ni size`

- [ ] **Step 2: Run test to verify it fails** — `./gradle.ps1 :feature:settings:testDebugUnitTest --tests "com.multistream.feature.settings.WidgetConfigViewModelTest"`. Expected: FAIL (firmas nuevas no existen).

- [ ] **Step 3: Implement** las firmas de arriba en `WidgetConfigViewModel`; marcar la antigua `addWidget(layout, url, x, y, w, h)` `@Deprecated` con delegación a la nueva (pantalla y tests existentes siguen compilando).

- [ ] **Step 4: Run test to verify it passes** — mismo comando. Expected: PASS (12/12 del fichero: los 5 previos + 7 nuevos).

- [ ] **Step 5: Commit**

```powershell
git add feature/settings/src/main/kotlin/com/multistream/feature/settings/WidgetConfigViewModel.kt feature/settings/src/test/kotlin/com/multistream/feature/settings/WidgetConfigViewModelTest.kt core/widgets/src/main/kotlin/com/multistream/core/widgets/WidgetLayoutManager.kt core/widgets/src/test/kotlin/com/multistream/core/widgets/WidgetLayoutManagerTest.kt
git commit -m "feat(settings): widget move/resize/centered-add in ViewModel"
```

### Task 5: `Panel` y `WidgetBox` (gestos táctiles)

**Files:**
- Create: `feature/settings/src/main/kotlin/com/multistream/feature/settings/Panel.kt` (incluye `WidgetBox`, `GuidesOverlay` y el estado de drag local — un solo fichero porque cambian juntos)
- Test: validación manual (Task 6); la lógica de conversión/snap ya está testada (Tasks 1–2)

**Interfaces:**
- Consumes: `PanelMapper` (Task 1), `SnapHelper`/`Guideline` (Task 2), `Widget` de `:core:common`, flows de `WidgetLayoutManager` vía ViewModel (Task 4).
- Produces (Task 6):
  - `@Composable fun Panel(layout: Layout, widgets: List<Widget>, snapEnabled: Boolean, showWebPreview: Boolean, selectedId: String?, onWidgetSelected: (String?) -> Unit, onMove: (id: String, layoutX: Float, layoutY: Float) -> Unit, onResize: (id: String, layoutW: Int, layoutH: Int) -> Unit, onUrlEdit: (id: String, url: String) -> Unit, onRemove: (id: String) -> Unit, modifier: Modifier = Modifier)`
  - Semántica: Box aspect-ratio (16:9 / 9:16) con `onSizeChanged` → `PanelDims`; cada widget = `WidgetBox` posicionado con `toTouchPosition` y tamaño en Float (`layoutW * panel.width / layoutW_del_layout`, sin redondeo); tap fuera de cajas → `onWidgetSelected(null)`.
  - PUNTO DE CONVERSIÓN ÚNICO: los gestos viven en px táctiles (estado local), pero `onMove`/`onResize` emiten YA convertidos a px-layout via `PanelMapper.toLayoutPosition`/`toLayoutSize` en el commit — el ViewModel no convierte nada (sus tests usan px-layout).
  - Guías de snap durante drag: estado Compose local; por frame se convierte la posición táctil actual con `PanelMapper` a px-layout y se predecen las guías con `SnapHelper` (sin tocar el ViewModel); se dibujan en px táctiles como líneas discontinuas rojas; desaparecen al soltar.
  - `WidgetBox(widget, selected, isDragging)`: el "modo editar" del spec §3 = `isDragging` (drag en curso). Cuerpo con `detectDragGestures`: cada delta suma a la posición táctil local — `remember(key = widget.id)` inicializado desde `widget.position` (el Box se puede recrear por recomposición sin perder el arrastre en curso) — commit en `onDragEnd`; `onDragCancel` (rotación/2.º dedo) DESCARTA el estado local (queda el último commit válido) y resetea `isDragging`; resize igual con el handle.
  - Handle ○ 24 dp en esquina inf-der solo si seleccionada, con su `pointerInput` ANTES (más interno/prioritario) que el del cuerpo, para que no se disputen el gesto en una caja mínima (~50dp táctiles).
  - Estado visual: seleccionada → borde azul; fuera parcial → amarillo discontinuo + ⚠ "fuera del panel (se recortará en el stream)"; fuera total → rojo "fuera del panel (no se verá)"; url vacía → borde rojo + "falta URL". `showWebPreview && selected && url no vacía` → `AndroidView(WebView)` con `loadUrl`; si no → placeholder con url y tamaño.
  - WebView y gesto: mientras el preview está visible, el WebView NO debe robar el drag — deshabilitar su interacción táctil mientras `isDragging`/preview (touch listener vacío o flag `isUserInputEnabled=false` de facto: `setOnTouchListener` devolviendo true), y durante un drag activo forzar placeholder (el spec: caja simple al arrastrar).
  - Fallo de carga del WebView (spec §6): estado `webLoadFailed` local → cae a placeholder con icono de error + botón "Reintentar" (`reload()`); el arrastre nunca depende de la web.

- [ ] **Step 1: Implement** `Panel.kt` con la interfaz exacta de arriba.
- [ ] **Step 2: Build check** — `./gradle.ps1 :feature:settings:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** (sin pantalla que lo use aún; se conecta en Task 6)

```powershell
git add feature/settings/src/main/kotlin/com/multistream/feature/settings/Panel.kt
git commit -m "feat(settings): tactile Panel/WidgetBox with drag and corner resize"
```

### Task 6: Integración en `WidgetConfigScreen` + validación manual

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/multistream/feature/settings/WidgetConfigScreen.kt`
- Modify: `app/src/main/kotlin/com/multistream/app/navigation/AppNavigation.kt` — SOLO si la ruta ya no compila (no debería: `WidgetConfigScreen()` sin args no cambia)

**Interfaces:**
- Consumes: `Panel` (Task 5), ViewModel methods (Task 4), flows existentes.
- Produces: pantalla completa; nada nuevo para otras tareas.

- [ ] **Step 1: Implement** — reemplaza cada `WidgetSection` por: cabecera (título + botón Add + toggles "Imán" y "Vista previa web", `rememberSaveable` por layout) y debajo su `Panel`; `AddWidgetDialog` se reduce a pedir SOLO la URL (sin campos X/Y/W/H) y llama `addWidget(layout, url)`; tras el alta el widget NACE SELECCIONADO (`onWidgetSelected(id del nuevo)`, spec §3); `onMove` → `moveWidget(id, layoutX, layoutY, snapEnabled)` (ya en px-layout); `onResize` → `resizeWidget(id, layoutW, layoutH)`; `onUrlEdit` → `setWidgetUrl`; edición in-situ: caja seleccionada con campo de URL (`OutlinedTextField` dentro de la caja) cuando no hay preview web; ✕ en la caja seleccionada → `removeWidget`; la lista de tarjetas actual bajo cada panel se conserva (con "falta URL" marcado en rojo) para recuperar widgets fuera-total. AHÍ SÍ: eliminar la `@Deprecated addWidget(layout, url, x, y, w, h)` del ViewModel y ACTUALIZAR los test antiguos de `WidgetConfigViewModelTest` que la usan (líneas 50-89) a la nueva firma.
- [ ] **Step 2: Build + tests** — `./gradle.ps1 :feature:settings:testDebugUnitTest :core:widgets:testDebugUnitTest :app:assembleDebug`. Expected: BUILD SUCCESSFUL, todos PASS.
- [ ] **Step 3: Validación manual en dispositivo/tablet** (SD8 Gen 3), criterio de hecho del spec:
  - M1: arrastrar con un dedo mueve; soltar persiste (rotar y volver mantiene); handle redimensiona, mínimo 100×100.
  - M2: widget fuera parcial → amarillo ⚠; fuera total → rojo; se recupera arrastrando.
  - M3: toggle "Imán" on/off cambia el comportamiento (guías rojas on; 1:1 off).
  - M4: rotar/2.º dedo a mitad de drag → último commit válido, sin crash ni tamaños 0.
  - M5: alta con URL vacía → borde rojo "falta URL", `StreamingScreen` lista el widget sin crashear; con URL válida y preview on → WebView en la caja seleccionada.
  - M6: `StreamingScreen` muestra los cambios y NO permite editar.
- [ ] **Step 4: Commit**

```powershell
git add feature/settings/src/main/kotlin/com/multistream/feature/settings/WidgetConfigScreen.kt
git commit -m "feat(settings): tactile widget panel editor in config screen"
```
