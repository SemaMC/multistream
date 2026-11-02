# Rediseño GUI v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Estructura A (Go Live + Configuración two-pane) con el bug de navegación eliminado por diseño, previews compactas y barra de estado verde/roja.

**Architecture:** Se reutilizan los composables existentes (`PlatformConfigScreen`, `WidgetConfigScreen`) embebidos en el pane derecho — no se duplican; las rutas intermedias `settings*` desaparecen del NavHost; `SettingRow` gana parámetro `selected`.

**Tech Stack:** Kotlin 2.4.21, Compose 1.9.0 + Material3 1.4.0, Navigation-Compose 2.9.0, navigation-suite 1.3.0 + adaptive 1.1.0, Hilt, JUnit4 + MockK.

**Spec:** `docs/superpowers/specs/2026-11-02-rediseno-gui-v2-design.md`

## Global Constraints

- compileSdk 36, targetSdk 36, minSdk 29; Java 17 de Adoptium.
- Shell PowerShell 5.1: sin `&&`; un comando por invocación.
- Repos fijos (`FAIL_ON_PROJECT_REPOS`): solo google()/mavenCentral()/jitpack.
- Tokens exactos v1: fondo `#0B1526`, superficie `#111F36`/`#0E1B30`, línea `#1E2F4D`, texto `#E8EEF7`/`#93A4C0`, acento `#3D9BFF`, ok `#34D17B`; un solo tema oscuro.
- Estado con `rememberSaveable`; prohibido `android:configChanges`.
- Textos de UI en inglés; diálogos de URL/key con IME tratado (`adjustResize` se mantiene).
- No cambiar contratos de ViewModels ni tocar `:core:*` / `:data:platforms`.
- Puerta de cada tarea: `.\gradlew.bat :feature:settings:testDebugUnitTest :feature:streaming-ui:testDebugUnitTest :feature:uikit:testDebugUnitTest` (PowerShell, workdir repo) + salida pegada. Task 5 añade `.\gradlew.bat :app:assembleDebug`.

## Review Focus

- Rotar el tablet con Widgets seleccionado: se espera que la opción siga marcada y el detalle siga siendo el editor de widgets.
- Primer tap en una opción: se espera el editor directamente a la derecha, sin segundo botón ni página intermedia.
- Flecha atrás de Configuración: se espera volver a Go Live, no a una pantalla fantasma.
- Barra de estado con 0 plataformas configuradas: se espera fila(s) en rojo/pendiente, no lista vacía sin explicación.
- Ancho de teléfono (Compact, ~360dp): se espera opciones + detalle apilados sin recorte ni scroll horizontal.

---

### Task 1: `SettingRow(selected)` en uikit

**Files:**
- Modify: `feature/uikit/src/main/kotlin/com/multistream/feature/uikit/SettingRow.kt` (nuevo parámetro `selected: Boolean = false`, pinta fila marcada con acento)
- Test: compilación + tests JVM existentes (sin compose-test en el repo)

**Interfaces:**
- Consumes: `MultistreamColors` (existente).
- Produces: `SettingRow(title, icon, trailing, selected, onClick)` para la Task 2.

- [ ] **Step 1: Añadir `selected: Boolean = false` a `SettingRow` y pintar la fila marcada (acento, sin ser solo color: mantener texto/icono).**
- [ ] **Step 2: Verificar puerta.**

Run: `.\gradlew.bat :feature:uikit:testDebugUnitTest`
Expected: PASS.
- [ ] **Step 3: Commit.**

```bash
git add feature/uikit
git commit -m "feat(ui): SettingRow selectable state"
```

### Task 2: Configuración two-pane (Plataformas | Widgets + detalle embebido)

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/multistream/feature/settings/SettingsScreen.kt` (reescribir: opciones Plataformas/Widgets a la izquierda con `SettingRow(selected)`, selección con `rememberSaveable`; detalle derecho embebe `PlatformConfigScreen()` / `WidgetConfigScreen()` tal cual, sin duplicarlos; flecha ← arriba-izquierda que vuelve atrás con `navController.popBackStack()`; en Compact apilar opciones + detalle en `Column` con scroll, sin páginas intermedias)
- Test: `PlatformConfigViewModelTest`, `WidgetConfigViewModelTest` existentes

**Interfaces:**
- Consumes: Task 1 (`SettingRow(..., selected)`).
- Produces: pantalla de Configuración usada por la Task 3 como destino `configuracion`.

- [ ] **Step 1: Reescribir `SettingsScreen` como two-pane con las 2 opciones, detalle embebido y flecha atrás. Sin tocar ViewModels ni el `Panel`.**
- [ ] **Step 2: Verificar puerta.**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest :feature:uikit:testDebugUnitTest`
Expected: PASS.
- [ ] **Step 2b: Pin Review Focus (rotación/selección): confirmar `rememberSaveable` en la opción y que el primer tap muestra el editor sin paso intermedio. Inspección + anotarlo.**
- [ ] **Step 3: Commit.**

```bash
git add feature/settings
git commit -m "feat(ui): two-pane Configuracion with embedded editors"
```

### Task 3: Navegación a 2 destinos (go-live + configuracion)

**Files:**
- Modify: `app/src/main/kotlin/com/multistream/app/navigation/AppNavigation.kt` (rutas `go-live` inicial y `configuracion`; eliminar como páginas `home`, `settings`, `settings/platforms`, `settings/widgets`, `streaming`, `streaming/{platform}` — `StreamingScreen` pasa a ser el contenido de `go-live`)
- Modify: `app/src/main/kotlin/com/multistream/app/navigation/RootScaffold.kt` (2 destinos: Go Live + Configuración con icono tuerca; `routePrefix` `go-live` y `configuracion`)
- Test: compilación + tests de features (sin cambios en ViewModels)

**Interfaces:**
- Consumes: Task 2 (pantalla de Configuración).
- Produces: destinos estables que usa la Task 4.

- [ ] **Step 1: Reescribir `AppNavigation` (2 rutas) y `RootScaffold` (2 items). `StreamingScreen` no cambia de firma.**
- [ ] **Step 2: Verificar puerta.**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest :feature:streaming-ui:testDebugUnitTest :feature:uikit:testDebugUnitTest`
Expected: PASS.
- [ ] **Step 2b: Pin Review Focus (atrás): confirmar que la flecha de Configuración vuelve a `go-live` y que el rail marca el destino actual en ambas rutas. Inspección + anotarlo.**
- [ ] **Step 3: Commit.**

```bash
git add app
git commit -m "feat(ui): two-destination navigation go-live plus configuracion"
```

### Task 4: Go Live compacto (estado + shortcut + tuerca)

**Files:**
- Modify: `feature/streaming-ui/src/main/kotlin/com/multistream/feature/streaming/StreamingScreen.kt` (previews reducidas incluida la vertical; controles y conteos como chips/botones flotantes; barra inferior de estado: fila por plataforma de `platforms` con dot verde/rojo + nombre; botón tuerca → `configuracion`; acceso directo a edición de widgets → `configuracion`; mantener `StreamingViewModel` y lógica de sesión intactos)
- Test: `StreamingViewModelServiceTest` y cía.

**Interfaces:**
- Consumes: Tasks 1–3 (componentes, rutas).

- [ ] **Step 1: Recomponer `StreamingScreen` compacto con barra de estado, shortcut y tuerca. Sin tocar `StreamingViewModel`.**
- [ ] **Step 2: Verificar puerta.**

Run: `.\gradlew.bat :feature:streaming-ui:testDebugUnitTest :feature:uikit:testDebugUnitTest`
Expected: PASS.
- [ ] **Step 2b: Pin Review Focus (vacío/Compact): con 0 plataformas, filas en rojo/pendiente con explicación; en Compact, sin recorte ni scroll horizontal. Inspección + anotarlo.**
- [ ] **Step 3: Commit.**

```bash
git add feature/streaming-ui
git commit -m "feat(ui): compact Go Live with status bar and shortcuts"
```

### Task 5: Puerta completa + APK al tablet + capturas

**Files:**
- Test: suite completa + `assembleDebug`; captura `screencap` del tablet por adb
- Test: ninguno nuevo

**Interfaces:**
- Consumes: Tasks 1–4.

- [ ] **Step 1: Verificar puerta completa.**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest :feature:streaming-ui:testDebugUnitTest :feature:uikit:testDebugUnitTest`
Expected: PASS. Después: `.\gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 2: Instalar en el tablet (`C:\Users\SemaMC\android-tools\sdk\platform-tools\adb.exe`, no está en PATH): `adb install -r app\build\outputs\apk\debug\app-debug.apk`; si no hay `device` conectado, BLOCKED con lo que falta (no inventar conexión).**
- [ ] **Step 3: Capturar `adb shell screencap` de Go Live y Configuración y dejar los PNG en `.superpowers/sdd/2026-11-02-rediseno-gui-v2/` para revisión visual del usuario. Sin commit de binarios.**
- [ ] **Step 4: Commit solo si quedó algo sin commitear de Tasks 1–4; si el árbol está limpio, no commitear.**

```bash
git status --short
```
