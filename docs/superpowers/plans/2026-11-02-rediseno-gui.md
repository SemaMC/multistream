# Rediseño GUI total Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar la UI actual por el sistema "azul noche" con navegación adaptativa y Ajustes two-pane en tablet, sin tocar `:core:*` ni `:data:platforms`.

**Architecture:** Nuevo módulo `:feature:uikit` con tokens + componentes compartidos; `app` monta `NavigationSuiteScaffold` (bottom bar / rail) sobre las rutas Nav2 existentes; cada pantalla se reestila contra sus ViewModels actuales sin cambiar sus contratos.

**Tech Stack:** Kotlin 2.4.21, Compose 1.9.0 + Material3 1.4.0, Navigation-Compose 2.9.0, `material3-adaptive:navigation-suite` (nueva), Hilt, JUnit4 + MockK.

**Spec:** `docs/superpowers/specs/2026-11-02-rediseno-gui-design.md`

## Global Constraints

- compileSdk 36, targetSdk 36, minSdk 29; Java 17 (gradle.properties ya fija Adoptium).
- Shell PowerShell 5.1: sin `&&`; un comando por invocación.
- Repos fijos (`FAIL_ON_PROJECT_REPOS`): solo google()/mavenCentral()/jitpack; ninguna dependencia fuera de ellos.
- Tokens exactos del spec: fondo `#0B1526`, superficie `#111F36`/`#0E1B30`, línea `#1E2F4D`, texto `#E8EEF7`/`#93A4C0`, acento `#3D9BFF`, ok `#34D17B`; un solo tema oscuro.
- Estado con `rememberSaveable`; prohibido `android:configChanges`.
- Textos de UI en inglés como hasta ahora (sin i18n nueva); diálogos de URL/key con IME tratado.
- No cambiar contratos de ViewModels existentes ni tocar `:core:*` / `:data:platforms`.
- Puerta de cada tarea: `.\gradlew.bat :feature:settings:testDebugUnitTest :feature:streaming-ui:testDebugUnitTest :feature:uikit:testDebugUnitTest` (en PowerShell, workdir repo) + salida pegada.

## Review Focus

- Teclado cubriendo los campos de RTMP URL / stream key en pantallas pequeñas: se espera que el campo enfocado siga visible (IME tratado, sin doble padding).
- Fuente al 200% o pantallas de 320dp: se espera que ninguna fila ni botón quede cortado o inalcanzable.
- Rotar la tablet con una sección de Ajustes seleccionada: se espera que la selección sobreviva.
- Tablet sin sección seleccionada: se espera placeholder en el detalle, no pantalla vacía ni crash.
- Texto secundario `#93A4C0` sobre `#0B1526`: se espera contraste AA en cuerpo y controles.

---

### Task 1: Dependencia navigation-suite con fallback

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Test: compilación (`:app:compileDebugKotlin`)

**Interfaces:**
- Consumes: nada.
- Produces: `libs.compose.navigation.suite` disponible para `app` (o decisión documentada de fallback).

- [ ] **Step 1: Añadir `compose-navigation-suite = { group = "androidx.compose.material3.adaptive", name = "navigation-suite", version = "1.3.0" }` a `[libraries]` y `implementation(libs.compose.navigation.suite)` en `app/build.gradle.kts`.**
- [ ] **Step 2: Verificar que resuelve y compila.**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Si falla por resolución (offline/transitorio), comprobar antes `.\gradlew.bat :app:dependencies --configuration debugCompileClasspath`; si el artefacto no baja, NO inventar limitaciones: documentar fallback en el commit (bottom-bar/rail manual con `WindowSizeClass`, sin dependencia nueva) y avisar en el informe.
- [ ] **Step 3: Commit.**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts docs/superpowers/plans/2026-11-02-rediseno-gui.md
git commit -m "feat(ui): navigation-suite dependency for adaptive nav"
```

### Task 2: Módulo `:feature:uikit` con tema y tokens testados

**Files:**
- Create: `feature/uikit/build.gradle.kts` (android library, compose, `namespace = "com.multistream.feature.uikit"`, minSdk 29, junit only en tests)
- Modify: `settings.gradle.kts` (añadir `include(":feature:uikit")`)
- Create: `feature/uikit/src/main/kotlin/com/multistream/feature/uikit/MultistreamColors.kt` (object con `Color(0xFF0B1526)` etc. según spec)
- Create: `feature/uikit/src/test/kotlin/com/multistream/feature/uikit/MultistreamColorsTest.kt`
- Test: `feature/uikit/src/test/...` (JVM, JUnit4, sin compose-test: el repo no tiene esa infra)

**Interfaces:**
- Consumes: Task 1 (nada directo).
- Produces: `MultistreamColors.{Background,Surface,SurfaceAlt,Outline,OnBackground,OnBackgroundMuted,Accent,Ok}` (`androidx.compose.ui.graphics.Color`) y `com.multistream.feature.uikit` como dependencia de features/app.

- [ ] **Step 1: Escribir el test que falla.**

```kotlin
@Test fun background_esAzulNoche() {
    assertEquals(Color(0xFF0B1526), MultistreamColors.Background)
}
@Test fun accent_esAzulElectrico() {
    assertEquals(Color(0xFF3D9BFF), MultistreamColors.Accent)
}
```

(más un assert por cada token del spec: Surface `0xFF111F36`, SurfaceAlt `0xFF0E1B30`, Outline `0xFF1E2F4D`, OnBackground `0xFFE8EEF7`, OnBackgroundMuted `0xFF93A4C0`, Ok `0xFF34D17B`.)
- [ ] **Step 2: Correr el test y verlo fallar.**

Run: `.\gradlew.bat :feature:uikit:testDebugUnitTest`
Expected: FAIL (clase no existe).
- [ ] **Step 3: Implementar `MultistreamColors` y el módulo (`build.gradle.kts` + `include` en settings) con los 8 valores exactos del spec.**
- [ ] **Step 4: Correr el test y verlo pasar.**

Run: `.\gradlew.bat :feature:uikit:testDebugUnitTest`
Expected: PASS.
- [ ] **Step 5: Commit.**

```bash
git add feature/uikit settings.gradle.kts
git commit -m "feat(ui): uikit module with MultistreamColors tokens"
```

### Task 3: `MultistreamTheme` + componentes compartidos

**Files:**
- Create: `feature/uikit/src/main/kotlin/com/multistream/feature/uikit/MultistreamTheme.kt` (solo oscuro: `darkColorScheme` con los tokens; `primary = Accent`)
- Create: `.../uikit/SettingRow.kt` (`SettingRow(title, icon, trailing)`), `SectionHeader.kt`, `StatusChip.kt` (dot + texto), `GoLiveButton.kt`, `PlatformRow.kt` (nombre con color de marca + chip)
- Test: compilación + `@Preview` (phone + tablet) revisadas por el usuario; sin compose-test en el repo.

**Interfaces:**
- Consumes: Task 2 (`MultistreamColors`).
- Produces: `MultistreamTheme(content)` y los 5 componentes con esas firmas para Tasks 4–8.

- [ ] **Step 1: Implementar `MultistreamTheme` + los 5 componentes con `@Preview` (una phone, una tablet). Reglas de componente: altura táctil mínima 48dp, `contentDescription` en todo icono, ningún estado comunicado solo por color.**
- [ ] **Step 2: Verificar que compila y los tests JVM siguen verdes.**

Run: `.\gradlew.bat :feature:uikit:testDebugUnitTest`
Expected: PASS.
- [ ] **Step 3: Commit.**

```bash
git add feature/uikit
git commit -m "feat(ui): MultistreamTheme and shared components"
```

### Task 4: Navegación raíz adaptativa (sin migrar a Nav3)

**Files:**
- Create: `app/src/main/kotlin/com/multistream/app/navigation/RootScaffold.kt` (usa `NavigationSuiteScaffold` de Task 1, o fallback manual documentado; destinos Inicio/Transmitir/Ajustes; aplica insets al contenido, NUNCA `safeDrawingPadding` en el scaffold padre)
- Modify: `app/src/main/kotlin/com/multistream/app/navigation/AppNavigation.kt` (mantiene las 5 rutas existentes; `home`/`streaming`/`settings` cuelgan del destino correspondiente)
- Modify: `app/src/main/kotlin/com/multistream/app/MainActivity.kt` (`MaterialTheme` → `MultistreamTheme`)
- Modify: `app/build.gradle.kts`, `feature/streaming-ui/build.gradle.kts`, `feature/settings/build.gradle.kts` (añadir `implementation(project(":feature:uikit"))` donde se use)
- Test: unit tests existentes de features.

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: destinos raíz estables que usan Tasks 5–8.

- [ ] **Step 1: Implementar `RootScaffold` + rewiring manteniendo nombres de ruta (`home`, `settings`, `settings/platforms`, `settings/widgets`, `streaming/{platform}`).**
- [ ] **Step 2: Verificar puerta del proyecto.**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest :feature:streaming-ui:testDebugUnitTest :feature:uikit:testDebugUnitTest`
Expected: PASS en los 3 módulos.
- [ ] **Step 2b: Pin Review Focus (insets): confirmar en `RootScaffold.kt` que el scaffold padre NO lleva `safeDrawingPadding` y que cada pantalla aplica `innerPadding` a su `contentPadding`. Sin comando: inspección + anotarlo en el informe.**
- [ ] **Step 3: Commit.**

```bash
git add app feature
git commit -m "feat(ui): adaptive root navigation with MultistreamTheme"
```

### Task 5: Home como dashboard

**Files:**
- Modify: `feature/streaming-ui/.../HomeScreen.kt` (usa `HomeViewModel.platforms` existente: hero con estado, `PlatformRow` por plataforma con dot/chip, `GoLiveButton` → `streaming/...`; mantiene navegación a `settings`)
- Test: `StreamingViewModelServiceTest` y cía. existentes.

**Interfaces:**
- Consumes: Tasks 3–4. No cambia `HomeViewModel`.

- [ ] **Step 1: Reescribir `HomeScreen` contra el contrato actual de `HomeViewModel`.**
- [ ] **Step 2: Verificar puerta.**

Run: `.\gradlew.bat :feature:streaming-ui:testDebugUnitTest :feature:uikit:testDebugUnitTest`
Expected: PASS.
- [ ] **Step 3: Commit.**

```bash
git add feature/streaming-ui
git commit -m "feat(ui): home dashboard with platform status"
```

### Task 6: Streaming adaptativa (tablet lado a lado)

**Files:**
- Modify: `feature/streaming-ui/.../StreamingScreen.kt` (mantiene `StreamingViewModel` + `captureLauncher` intactos; `WindowSizeClass`: Expanded → preview + controles/stats en `Row`, Compact → `Column`; elimina el volcado de texto de widgets por un resumen con conteo)
- Test: tests existentes del módulo.

**Interfaces:**
- Consumes: Tasks 3–4. No cambia `StreamingViewModel` ni `LayoutPreview` (solo la composición).

- [ ] **Step 1: Recomponer `StreamingScreen` adaptativa sin tocar lógica de sesión; el volcado de texto de widgets se sustituye por un resumen con conteo (diseño aprobado por el usuario en la ronda de brainstorming).**
- [ ] **Step 2: Verificar puerta.**

Run: `.\gradlew.bat :feature:streaming-ui:testDebugUnitTest :feature:uikit:testDebugUnitTest`
Expected: PASS.
- [ ] **Step 3: Commit.**

```bash
git add feature/streaming-ui
git commit -m "feat(ui): adaptive streaming screen"
```

### Task 7: Ajustes two-pane estilo Streamlabs

**Files:**
- Modify: `feature/settings/.../SettingsScreen.kt` (lista de secciones con `SettingRow`: Cuenta, Multistream, Streaming, Alertas, Eventos, Chat, Widgets; en Expanded lista 320–360dp + detalle con placeholder sin selección, selección con `rememberSaveable`; en Compact lista full que navega a `settings/platforms` y `settings/widgets`; secciones sin backend como fila deshabilitada "próximamente")
- Test: `PlatformConfigViewModelTest`, `WidgetConfigViewModelTest` existentes.

**Interfaces:**
- Consumes: Tasks 3–4.

- [ ] **Step 1: Reescribir `SettingsScreen` two-pane manteniendo rutas de detalle existentes.**
- [ ] **Step 2: Verificar puerta.**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest :feature:uikit:testDebugUnitTest`
Expected: PASS.
- [ ] **Step 2b: Pin Review Focus (rotación/placeholder): confirmar que la sección seleccionada usa `rememberSaveable` y que sin selección el detalle muestra placeholder. Sin comando: inspección + anotarlo en el informe.**
- [ ] **Step 3: Commit.**

```bash
git add feature/settings
git commit -m "feat(ui): two-pane settings hub"
```

### Task 8: Plataformas y Widgets con el nuevo tema

**Files:**
- Modify: `feature/settings/.../PlatformConfigScreen.kt` (filas `PlatformRow` con color de marca + chip Conectado/nombre; diálogo de credenciales igual pero temado, campos con IME tratado, `contentPadding` con `innerPadding`)
- Modify: `feature/settings/.../WidgetConfigScreen.kt` (envolver en tema, mantener `Panel` táctil y su lógica intactos)
- Modify: `app/src/main/AndroidManifest.xml` (fijar `android:windowSoftInputMode="adjustResize"` en `.MainActivity`)
- Test: tests existentes del módulo.

**Interfaces:**
- Consumes: Tasks 3, 7. No cambia `PlatformConfigViewModel` ni `WidgetConfigViewModel`.

- [ ] **Step 1: Reestilar ambas pantallas sin tocar ViewModels ni el `Panel`.**
- [ ] **Step 1b: Fijar `android:windowSoftInputMode="adjustResize"` en el `<activity android:name=".MainActivity">` de `app/src/main/AndroidManifest.xml` (hoy ausente: verificado 0 matches de `windowSoftInputMode`).**
- [ ] **Step 2: Verificar puerta completa + ensamblado debug.**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest :feature:streaming-ui:testDebugUnitTest :feature:uikit:testDebugUnitTest`
Expected: PASS. Después: `.\gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 2b: Pin Review Focus (IME/contraste/zoom): confirmar `imePadding`/`fitInside` en el diálogo de credenciales y `adjustResize` aplicado del Step 1b; confirmar que el texto secundario usa el token `OnBackgroundMuted` sobre fondo (contraste AA) y que filas/botón no se recortan a 320dp ni con fuente al 200% (sin tamaños fijos, mínimos 48dp). Sin comando: inspección + anotarlo en el informe.**
- [ ] **Step 3: Commit.**

```bash
git add feature/settings app/src/main/AndroidManifest.xml
git commit -m "feat(ui): platform and widget screens in MultistreamTheme"
```
