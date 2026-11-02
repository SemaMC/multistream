# Rediseño GUI total — design spec (2026-11-02)

Estado: aprobado por el usuario vía maqueta (azul #3D9BFF sobre #0B1526).

## Alcance
Sistema completo de la capa UI: tema, navegación, Home, Transmitir, Ajustes two-pane, Plataformas y Widgets. Sin tocar `:core:*` ni `:data:platforms`.
No entra: migración a Navigation 3, OAuth nuevo, cambios de encoding/streaming/audio, multi-view v1.1, TikTok.

## Decisiones y motivos
1. Sistema completo en vez de solo reestilo. Motivo: la queja es estructural (tablet estira listas); el reestilo no la cierra.
2. `NavigationSuiteScaffold` (bottom bar en teléfono, rail en tablet) sin migrar a Navigation 3. Motivo: el repo usa NavHost/Nav2 (`AppNavigation.kt`); migrar reescribe navegación y wiring con alto blast radius para el mismo resultado visual.
3. Tema único oscuro "azul noche": fondo `#0B1526`, superficie `#111F36`/`#0E1B30`, línea `#1E2F4D`, texto `#E8EEF7`/`#93A4C0`, acento `#3D9BFF`, ok `#34D17B`. Motivo: pedido explícito del usuario (Streamlabs pero en azul; hex aprobado en maqueta) y app de directo usada con poca luz.
4. Two-pane manual en Ajustes con `WindowSizeClass` (lista 320–360dp + detalle; placeholder sin selección). Motivo: cierra el hueco tablet sin añadir `adaptive-navigation3`.
5. Cada fila de Ajustes mapea a pantallas/ViewModels existentes; secciones sin backend quedan como fila deshabilitada "próximamente". Motivo: no inventar funcionalidad.

## Componentes nuevos (nombres)
- `MultistreamTheme` (tokens del punto 3, solo oscuro), `SettingRow` (icono + título + chevron/chip), `PlatformRow` (color de marca + chip Conectado/nombre), `SectionHeader`, `StatusChip`, `GoLiveButton`.
- Destinos raíz: Inicio, Transmitir, Ajustes. Ajustes en `Compact`: lista full + push; en `Expanded`: two-pane.
- Streaming en tablet: preview + controles/stats lado a lado; en teléfono apilado. Home pasa a dashboard (estado Shizuku, plataformas con dot, CTA transmitir).

## Reglas transversales
- Insets: `Scaffold innerPadding` → `contentPadding` de listas; nada de `safeDrawingPadding` en el `NavigationSuiteScaffold` padre. Diálogos de URL/key con IME bien tratado (`adjustResize` + `imePadding`/`fitInside`).
- Estado con `rememberSaveable` (como ya hace el panel de widgets); prohibido `android:configChanges`. Targets 48dp, `contentDescription` en iconos, sin color como único estado, contraste AA.

## Verificación del cambio (puerta del proyecto)
`.\gradlew.bat :feature:settings:testDebugUnitTest :feature:streaming-ui:testDebugUnitTest` en PowerShell (sin `&&`), más previews teléfono/tablet revisadas por el usuario. El repo no tiene `AGENTS.md`; este spec fija el comando hasta que exista.
