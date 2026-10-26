# Multistream Android App — Spec Document

## Contexto del proyecto

App Android para streaming simultáneo a múltiples plataformas (Twitch, YouTube, Kick, Facebook). **TikTok fuera del MVP** por riesgos de ToS y APIs no oficiales. Objetivo: "OBS para Android" con widgets personalizables, alto rendimiento (SD8 Gen 3), y integración profunda con Shizuku para capacidades privilegiadas.

## Decisiones de scope

- **MVP (v1.0)**: Multi-stream 4 plataformas (Twitch, YouTube, Kick, Facebook) + multi-layout (horizontal 16:9 para YouTube/Twitch/Kick/Facebook + vertical 9:16 para TikTok cuando se agregue) + widgets independientes por layout + Shizuku opcional
- **v1.1**: Sistema de escenas tipo OBS + variantes de encode por plataforma + TikTok (si APIs/ToS lo permiten)
- **v1.2**: Mic WiFi + RTMP server

## Decisiones clave

- **Lenguaje**: Kotlin nativo + Jetpack Compose
- **Arquitectura**: Multi-módulo Gradle pragmático
- **Shizuku**: Opcional (no requerido). Shizuku desbloquea features avanzadas pero no es necesario para el MVP.
- **Widgets**: URL-based (estilo Streamlabs/StreamElements) + nativos donde Streamlabs no cubre
- **Auth**: OAuth híbrido (donde sea fácil) + manual fallback
- **Captura**: Multi-source system (pantalla, cámara, imagen, video, mic)
- **iOS**: Android first, port iOS después

---

## Sección 1: Arquitectura de módulos

```
:app                          → UI principal, navegación, composition root
:core:shizuku                 → Shizuku UserService wrapper, features avanzadas (virtual display, FGS protection)
:core:capture                 → Fuentes de video (MediaProjection o virtual display, cámara, imagen, video file)
:core:audio                   → AudioPlaybackCapture API, DSP chain, mezcla
:core:encoding                → MediaCodec, composición de frames + overlays via OpenGL ES
:core:streaming               → RTMP multi-output via rtmp-client, reconexión con política de buffer
:core:widgets                 → WebView widgets, renderizado directo al Surface del compositor, comunicación
:data:platforms               → Auth, chat, stats por plataforma (Twitch IRC/EventSub, YouTube Live Chat, etc.)
:feature:streaming-ui         → Pantalla de streaming, controles, stats
:feature:settings             → Configuración de plataformas, fuentes, widgets
```

**Flujo de datos principal:**
```
Fuentes (pantalla via MediaProjection/virtual display, cámara, mic, audio sistema via AudioPlaybackCapture)
    ↓
Composición OpenGL ES (mezcla video + overlays renderizados directamente al Surface del compositor)
    ↓
Encoding MediaCodec H.264/AAC (perfil único compartido)
    ↓
Distribución RTMP multi-output (4 destinos, mismo encode, muxing FLV independiente por conexión)
```

**Perfil de encode único (MVP):**
- Codec: H.264 (máxima compatibilidad con todas las plataformas)
- Resolución: 1920x1080 @ 60fps (objetivo)
- Bitrate: 6 Mbps (objetivo), adaptativo según red
- Audio: AAC 128 kbps stereo
- Todas las plataformas reciben el mismo encode; variantes por plataforma → v1.1

**Dependencias clave:**
- **rtmp-client** (`io.github.thibaultbee:rtmp-client`) — RTMP low-level
  - ⚠️ **PENDIENTE DE VALIDACIÓN**: Soporte RTMPS (TLS) no verificado. Facebook requiere RTMPS en puerto 443.
  - **Spike obligatorio de 1 día** antes de implementación: validar handshake RTMPS contra `rtmps://live-api-s.facebook.com:443/rtmp/` y endpoint RTMPS de Kick
  - Si no soporta RTMPS: alternativas son wrapper TLS propio, otra lib (ej. `librtmp` con OpenSSL), o fork de `rtmp-client`
- **Oboe** — baja latencia para audio
- **Shizuku API** (`dev.rikka.shizuku:api`) — integración con Shizuku (opcional)
- **Jetpack Compose** + Material 3 — UI
- **AndroidX WebKit** — capa sobre WebView del sistema
- **CameraX** — API de cámara moderna para captura de video
- **ExoPlayer / Media3** — reproducción de video file para overlays y transiciones
- **Hilt** (`com.google.dagger:hilt-android`) — inyección de dependencias (framework DI)
- **Ktor** (`io.ktor:ktor-client-*`) — cliente HTTP/WebSocket para APIs de plataformas y eventos en tiempo real
- **kotlinx-coroutines** (`org.jetbrains.kotlinx:kotlinx-coroutines-*`) — programación asíncrona (todo el diseño es Flow-based)
- **DataStore** (`androidx.datastore:datastore-preferences`) — almacenamiento de preferencias y configuración

---

## Sección 2: Sistema de captura

**Modo primario (sin Shizuku):**
MediaProjection con consentimiento por sesión (Android 14+ requiere re-consentimiento).

```kotlin
// :core:capture/src/main/kotlin/.../MediaProjectionDisplayCapture.kt
class MediaProjectionDisplayCapture @Inject constructor(
    private val context: Context
) : DisplayCapture {
    override fun createVirtualDisplay(...): Surface {
        // MediaProjection + VirtualDisplay tradicional
        // Requiere diálogo de usuario cada sesión (Android 14+)
    }
}
```

**Modo mejorado (con Shizuku):**
Captura del display completo via virtual display con uid shell (sin MediaProjection, sin diálogo).

```kotlin
// :core:capture/src/main/kotlin/.../ShizukuDisplayCapture.kt
class ShizukuDisplayCapture @Inject constructor(
    private val shizukuService: ShizukuService
) : DisplayCapture {
    override fun createVirtualDisplay(width: Int, height: Int, dpi: Int): Surface {
        // Shizuku UserService con uid shell crea virtual display
        // usando DisplayManager.createVirtualDisplay + CAPTURE_SECURE_VIDEO_OUTPUT
        // (similar a scrcpy)
    }
}
```

**Fuentes de video adicionales:**

| Fuente | Implementación | Notas |
|---|---|---|
| **Cámara frontal/trasera** | `CameraX` / `Camera2` | Soporte para múltiples cámaras simultáneas |
| **Imagen estática** | `Bitmap` → `Surface` | Para logos, overlays estáticos |
| **Video file** | `ExoPlayer` / `Media3` → `Surface` | Para intros, transiciones, videos pregrabados |

**Limitaciones documentadas:**
- Apps con `FLAG_SECURE` (Netflix, banking apps) aparecen negro en captura — aceptable para gaming
- No hay captura selectiva por-app (eso requeriría work interno de system_server no disponible)

---

## Sección 3: Sistema de audio

**API de captura: AudioPlaybackCapture (Android 10+, verificada en Android 16)**

**Solución principal**: Usar `AudioPlaybackCapture` API (Android 10+), que:
- ✅ Captura audio de juegos sin silenciar el dispositivo
- ✅ Funciona en Android 16 (API 36)
- ✅ Audio suena en el dispositivo Y se captura simultáneamente
- ❌ Apps con DRM (Netflix, banking) pueden optar por no ser capturadas (aceptado)

```kotlin
// :core:audio/src/main/kotlin/.../GameAudioCapture.kt
class GameAudioCapture @Inject constructor(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    
    fun startCapture(mediaProjection: MediaProjection): Flow<ShortArray> {
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)  // Captura juegos
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA) // Captura multimedia
            .excludeMatchingUsage(AudioAttributes.USAGE_NOTIFICATION) // Excluye notificaciones
            .excludeMatchingUsage(AudioAttributes.USAGE_ALARM)
            .build()
        
        audioRecord = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build())
            .setBufferSizeInBytes(4096)
            .build()
        
        audioRecord?.startRecording()
        
        return flow {
            val buffer = ShortArray(2048)  // 4096 bytes (2048 shorts × 2 bytes/short)
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    emit(buffer.copyOf(read))
                }
            }
        }
    }
    
    fun stopCapture() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
```

**Limitaciones documentadas:**
- Apps con `FLAG_SECURE` o que optan por no permitir captura NO serán capturadas
- Esto incluye: Netflix, banking apps, algunos juegos con DRM
- Para juegos normales (90%+ de los casos): funciona correctamente
- **Requiere MediaProjection token** (diálogo de consentimiento en Android 14+, re-consent por sesión)

**Shizuku para audio**: Ya NO es necesario para captura de audio (AudioPlaybackCapture es API pública). Shizuku solo se usa para:
- Virtual displays avanzados (evitar diálogo MediaProjection)
- Background service protection (contra OEMs agresivos)
- Input injection (v1.2+)

**Fuentes de audio adicionales:**

| Fuente | Implementación | Notas |
|---|---|---|
| **Micrófono del dispositivo** | `AudioRecord` | Con DSP chain opcional |
| **Audio del sistema** | AudioPlaybackCapture (API pública, Android 10+) | Captura de juegos y multimedia |
| **Alertas (sonidos de follow/sub)** | Generados localmente, mezclados en DSP | No son "capturados", son inyectados |
| **(v1.2) Mic WiFi** | Streaming de audio por red (RTP con FEC/jitter buffer) | Fuente remota |

**DSP chain:**

```kotlin
// :core:audio/src/main/kotlin/.../DSPChain.kt
class DSPChain {
    private val effects: MutableList<AudioProcessor> = mutableListOf()
    
    fun addNoiseGate(thresholdDb: Float) { ... }
    fun addEQ(bands: List<EQBand>) { ... }  // 10-band parametric EQ
    fun addCompressor(threshold: Float, ratio: Float, attack: Float, release: Float) { ... }
    fun addReverb(roomSize: Float, damping: Float) { ... }
    
    // Procesamiento in-place para evitar allocations en hot path
    fun process(buffer: ShortArray) {
        for (effect in effects) {
            effect.process(buffer)
        }
    }
}
```

**Mezclador de audio:**

```kotlin
// :core:audio/src/main/kotlin/.../AudioMixer.kt
class AudioMixer {
    fun mix(mic: ShortArray?, systemAudio: ShortArray?, alerts: ShortArray?): ShortArray {
        // Mezcla ponderada de fuentes
        // mic (procesado con DSP) + system audio + alertas
    }
}
```

**Flujo de audio:**
```
Micrófono → DSP chain (noise gate, EQ, compressor) → ┐
Audio sistema (AudioPlaybackCapture) → ───────────────┤→ Mezclador → AAC encoding → RTMP
Alertas (generadas localmente) → ──────────────────────┘
(v1.2) Mic WiFi (RTP) → ───────────────────────────────┘
```

**Oboe para baja latencia:**
Oboe (C++ library con wrapper Kotlin) para procesamiento de audio en tiempo real. Latencia <10ms. Callbacks en C++ para evitar overhead de JNI en hot path.

---

## Sección 4: Sistema de encoding y composición

**Composición multi-layout (horizontal + vertical simultáneo):**

El sistema de composición debe soportar dos layouts simultáneos desde el MVP:
- **Layout horizontal** (1920x1080, 16:9): para Twitch, YouTube, Kick, Facebook
- **Layout vertical** (1080x1920, 9:16): preparado para TikTok cuando se agregue en v1.1

```kotlin
// :core:encoding/src/main/kotlin/.../MultiLayoutCompositor.kt
class MultiLayoutCompositor @Inject constructor() {
    // Dos pipelines de composición independientes
    private val horizontalCompositor: VideoCompositor
    private val verticalCompositor: VideoCompositor
    
    fun composeAll(sources: List<VideoSource>, overlays: List<Overlay>): CompositionResult {
        return CompositionResult(
            horizontal = horizontalCompositor.compose(sources, overlays, Layout.HORIZONTAL),
            vertical = verticalCompositor.compose(sources, overlays, Layout.VERTICAL)
        )
    }
}

data class CompositionResult(
    val horizontal: Surface,  // 1920x1080 output
    val vertical: Surface     // 1080x1920 output
)

enum class Layout { HORIZONTAL, VERTICAL }
```

**Encoding dual (dos pipelines MediaCodec):**

```kotlin
// :core:encoding/src/main/kotlin/.../DualVideoEncoder.kt
class DualVideoEncoder @Inject constructor() {
    private val horizontalEncoder: VideoEncoder
    private val verticalEncoder: VideoEncoder
    
    fun configure(): DualEncoderResult {
        val horizontalSurface = horizontalEncoder.configure(1920, 1080, 60, 6_000_000)
        val verticalSurface = verticalEncoder.configure(1080, 1920, 60, 4_000_000)
        return DualEncoderResult(horizontalSurface, verticalSurface)
    }
    
    fun encode(): DualEncodedStream {
        return DualEncodedStream(
            horizontal = horizontalEncoder.encode(),
            vertical = verticalEncoder.encode()
        )
    }
}
```

**Distribución por layout:**

```kotlin
// :core:streaming/src/main/kotlin/.../LayoutRouter.kt
class LayoutRouter @Inject constructor() {
    fun route(platform: Platform): Layout {
        return when (platform) {
            Platform.TWITCH, Platform.YOUTUBE, Platform.KICK, Platform.FACEBOOK -> Layout.HORIZONTAL
            // Platform.TIKTOK -> Layout.VERTICAL  // Deshabilitado hasta v1.1
        }
    }
}
```

**Renderizado de widgets:**
Widgets se renderizan **directamente al Surface del compositor** usando `WebView.draw(Canvas) → Bitmap → textura GL`, sin overlay window. Cada layout tiene sus propios widgets independientes.

```kotlin
// :core:widgets/src/main/kotlin/.../WidgetRenderer.kt
class WidgetRenderer {
    fun render(
        widgets: List<Widget>,
        targetSurface: Surface,
        layout: Layout  // HORIZONTAL o VERTICAL
    ) {
        // WebView.draw(Canvas) renderiza a Bitmap
        // Bitmap se sube como textura GL
        // Textura se compone con el video en el Surface del encoder
        // Layout determina la resolución y posición de widgets
    }
}
```

**Nota:** El overlay window solo se usa para preview local interactivo (opcional). Para el stream, los widgets son pasivos.

**Encoding MediaCodec:**

```kotlin
// :core:encoding/src/main/kotlin/.../VideoEncoder.kt
class VideoEncoder @Inject constructor() {
    fun configure(width: Int, height: Int, fps: Int, bitrate: Int) {
        // MediaCodec con H.264 baseline profile
        // Input surface recibe frames de VideoCompositor
        // Output: H.264 NAL units (chunks, no 1:1 por frame)
    }
    
    fun encode(): Flow<EncodedChunk> {
        // Flow de chunks (IDR, P-frames, SPS/PPS)
        // Cada chunk tiene: data, timestamp, isKeyFrame
    }
}
```

**Rendimiento y térmica:**

```kotlin
// :core:encoding/src/main/kotlin/.../PerformanceMonitor.kt
class PerformanceMonitor {
    fun monitor() {
        // Monitorea:
        // - FPS real vs objetivo (para cada layout)
        // - Temperatura de CPU/GPU
        // - Bitrate de red
        // Si throttling térmico: reduce bitrate automáticamente
        // Si red lenta: reduce bitrate o drop frames (no keyframes)
    }
}
```

**Objetivos de rendimiento (SD8 Gen 3):**
- 2 composiciones simultáneas + 2 encoders: 1080p60 (horizontal) + 1080x1920p60 (vertical)
- Overhead de composición GL: <25% GPU, <15% CPU (dual layout)
- Throttling térmico esperado después de 30-60 min de stream continuo
- Si ambos layouts están activos, el sistema prioriza calidad sobre framerate

---

## Sección 5: Sistema de streaming RTMP

**Multi-output RTMP con routing por layout:**

```kotlin
// :core:streaming/src/main/kotlin/.../RTMPMultiStreamer.kt
class RTMPMultiStreamer @Inject constructor(
    private val layoutRouter: LayoutRouter
) {
    private val connections = mutableListOf<RTMPConnection>()
    
    suspend fun connect(configs: List<PlatformConfig>) {
        // Cada conexión es independiente
        // Fallo de una no afecta a las demás
        for (config in configs) {
            val layout = layoutRouter.route(config.platform)
            val conn = RTMPConnection(config, layout)
            connections.add(conn)
            conn.connectAsync()  // No bloquea
        }
    }
    
    fun send(chunk: EncodedChunk, layout: Layout) {
        // Envía el chunk encoded solo a conexiones del layout correspondiente
        for (conn in connections) {
            if (conn.isConnected && conn.layout == layout) {
                conn.sendChunk(chunk)
            }
        }
    }
}

class RTMPConnection(
    private val config: PlatformConfig,
    val layout: Layout  // HORIZONTAL o VERTICAL
) {
    // ... implementación existente
}
```

**Flujo de datos dual:**

```
Fuentes (pantalla, cámara, mic, audio sistema)
    ↓
MultiLayoutCompositor (mezcla video + widgets)
    ├──→ Layout.HORIZONTAL (1920x1080) → VideoEncoder → RTMP (Twitch, YouTube, Kick, Facebook)
    └──→ Layout.VERTICAL (1080x1920) → VideoEncoder → RTMP (preparado para TikTok en v1.1)
```

**Configuración de plataformas (perfil único por layout):**

```kotlin
data class PlatformConfig(
    val platform: Platform,  // TWITCH, YOUTUBE, KICK, FACEBOOK, CUSTOM_RTMP
    val rtmpUrl: String,     // rtmps:// para Facebook/Kick
    val streamKey: String,
    val bitrate: Int         // Compartido por todas las plataformas (v1.0)
    // Nota: resolution, fps, codec son compartidos.
    // Variantes por plataforma (ej. TikTok con bitrate menor) → v1.1
)
```

**Reconexión con política de buffer:**

```kotlin
// :core:streaming/src/main/kotlin/.../RTMPConnection.kt
class RTMPConnection(private val config: PlatformConfig) {
    private val buffer = CircularBuffer<EncodedChunk>(maxSizeMb = 10, maxAgeSeconds = 5)
    
    suspend fun connect() {
        // Exponential backoff: 1s, 2s, 4s, 8s, max 30s
        // Mientras se reconecta, chunks se acumulan en buffer
        // Si buffer lleno: descarta chunks más viejos (no keyframes)
    }
    
    fun sendChunk(chunk: EncodedChunk) {
        if (isConnected) {
            // Si chunk es keyframe y timestamp es muy viejo (>2s), descarta P-frames intermedios
            // Reinicia timestamps desde último keyframe
            muxer.writeVideoChunk(chunk)
        } else {
            buffer.add(chunk)
        }
    }
    
    fun onReconnected() {
        // Reinicia desde último keyframe en buffer
        // Descarta todo lo anterior
        val lastKeyframe = buffer.lastKeyframe()
        muxer.startNewSegment(lastKeyframe.timestamp)
        for (chunk in buffer.from(lastKeyframe)) {
            muxer.writeVideoChunk(chunk)
        }
    }
}
```

**Compatibilidad de plataformas:**
- **Twitch**: H.264-only en ingest RTMP, soporta hasta 1080p60
- **YouTube**: H.264/H.265, hasta 4K
- **Kick**: H.264, RTMP/RTMPS (verificar con spike)
- **Facebook**: RTMPS requerido (`rtmps://live-api-s.facebook.com:443/rtmp/`) — **depende de spike RTMPS**

---

## Sección 6: Sistema de widgets

**Widgets independientes por layout:**

Cada layout (horizontal/vertical) tiene su propia configuración de widgets. Los widgets se renderizan directamente al Surface del compositor correspondiente.

```kotlin
// :core:widgets/src/main/kotlin/.../WidgetLayoutManager.kt
class WidgetLayoutManager @Inject constructor() {
    // Configuraciones separadas para cada layout
    private val horizontalWidgets: MutableStateFlow<List<Widget>> = MutableStateFlow(emptyList())
    private val verticalWidgets: MutableStateFlow<List<Widget>> = MutableStateFlow(emptyList())
    
    fun getWidgetsForLayout(layout: Layout): StateFlow<List<Widget>> {
        return when (layout) {
            Layout.HORIZONTAL -> horizontalWidgets
            Layout.VERTICAL -> verticalWidgets
        }
    }
    
    fun addWidget(widget: Widget, layout: Layout) {
        when (layout) {
            Layout.HORIZONTAL -> horizontalWidgets.update { it + widget }
            Layout.VERTICAL -> verticalWidgets.update { it + widget }
        }
    }
}

data class Widget(
    val id: String,
    val url: String,
    val position: WidgetPosition,
    val size: WidgetSize,
    val layout: Layout  // HORIZONTAL o VERTICAL
)
```

**Widgets URL-based:**

```kotlin
// :core:widgets/src/main/kotlin/.../WidgetWebView.kt
@Composable
fun WidgetWebView(url: String, onInteraction: (WidgetEvent) -> Unit) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() { ... }
                loadUrl(url)
            }
        }
    )
}
```

**Renderizado de widgets sobre video (ruta simplificada):**
Widgets se renderizan **directamente al Surface del compositor** sin overlay window:
- WebView con `draw(Canvas)` → Bitmap → Surface del compositor OpenGL
- O Compose nativo para widgets simples (texto, imágenes estáticas)
- **Sin `SYSTEM_ALERT_WINDOW`** para composición de stream (el overlay solo queda para preview local si se necesita interactividad)
- Consecuencia: widgets en el stream son **pasivos** (no interactivos). Preview local puede ser interactivo con overlay opcional.

**Permisos requeridos:**
La app solicita explícitamente todos los permisos necesarios durante el onboarding, explicando qué funcionalidad habilita cada uno:

| Permiso | Funcionalidad | Requerido |
|---|---|---|
| `RECORD_AUDIO` | Captura de micrófono para stream | Sí |
| `CAMERA` | Cámara frontal/trasera como fuente | Opcional (si usa cámara) |
| `FOREGROUND_SERVICE` | Mantener stream activo en background | Sí |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Captura de pantalla + streaming | Sí |
| `FOREGROUND_SERVICE_CAMERA` | Cámara en background | Sí si usa cámara (API 34+) |
| `FOREGROUND_SERVICE_MICROPHONE` | Micrófono en background | Sí (API 34+) |
| `SYSTEM_ALERT_WINDOW` | Overlay para widgets en preview local (opcional, interactividad) | No (solo si quieres preview interactivo) |
| `POST_NOTIFICATIONS` | Notificaciones de estado de stream | Opcional |
| `INTERNET` | Streaming RTMP a plataformas | Sí |
| `ACCESS_NETWORK_STATE` | Monitoreo de red para adapt bitrate | Sí |
| `WAKE_LOCK` | Evitar sleep durante stream | Sí |

**Flujo de solicitud de permisos:**
1. App solicita RECORD_AUDIO + FOREGROUND_SERVICE_MEDIA_PROJECTION/CAMERA/MICROPHONE
2. **`SYSTEM_ALERT_WINDOW`**: No requerido para MVP. Solo si se quiere preview interactivo con overlay (opcional).
3. Si usuario deniega CAMERA: función de cámara deshabilitada (resto funciona)
4. Si usuario deniega RECORD_AUDIO: stream sin micrófono (solo audio del juego)

**Widgets nativos (para lo que Streamlabs no cubre):**

| Widget | Implementación |
|---|---|
| **Chat de Twitch** | Widget URL de Streamlabs/StreamElements |
| **Chat de YouTube** | Widget URL |
| **Chat de Kick** | Widget URL o nativo (Kick tiene API pública) |
| **Chat de Facebook** | Widget URL |
| **Alertas (follow, sub, donation)** | Widget URL de Streamlabs/StreamElements |
| **Cámara PiP** | Fuente de video (ya soportada) |
| **Texto personalizado** | Fuente de video (imagen estática con texto renderizado) |

**Sistema de eventos:**

```kotlin
// :core:widgets/src/main/kotlin/.../WidgetEventBus.kt
class WidgetEventBus {
    fun sendEvent(event: StreamEvent) {
        // Envía eventos a los widgets URL via postMessage
    }
}

sealed class StreamEvent {
    data class Follow(val platform: Platform, val username: String) : StreamEvent()
    data class Subscription(val platform: Platform, val username: String, val months: Int) : StreamEvent()
    data class Donation(val platform: Platform, val username: String, val amount: Float) : StreamEvent()
    data class ChatMessage(val platform: Platform, val username: String, val message: String) : StreamEvent()
}
```

**Conexión con plataformas (vive en :data:platforms):**
- Twitch: IRC + EventSub (WebSocket)
- YouTube: YouTube Live Chat API (requiere OAuth con scopes verificados)
- Kick: API pública
- Facebook: Graph API (requiere revisión de app para `publish_video`)

---

## Sección 7: Autenticación y onboarding

**OAuth + manual fallback:**

```kotlin
// :data:platforms/src/main/kotlin/.../PlatformAuth.kt
interface PlatformAuth {
    suspend fun login(): PlatformCredentials
}

class TwitchAuth @Inject constructor() : PlatformAuth {
    override suspend fun login(): PlatformCredentials {
        // OAuth 2.0 flow con Twitch
    }
}

class YouTubeAuth @Inject constructor() : PlatformAuth {
    override suspend fun login(): PlatformCredentials {
        // OAuth 2.0 con Google
        // Requiere scopes verificados: youtube.force-ssl
        // Lead time de semanas para aprobación
    }
}

class FacebookAuth @Inject constructor() : PlatformAuth {
    override suspend fun login(): PlatformCredentials {
        // OAuth 2.0 con Facebook
        // Requiere revisión de app para publish_video
        // Lead time de semanas
    }
}

class ManualRtmpAuth @Inject constructor() : PlatformAuth {
    override suspend fun login(): PlatformCredentials {
        // Usuario ingresa RTMP URL + stream key manualmente
    }
}
```

**Almacenamiento seguro de credenciales:**

```kotlin
// :data:platforms/src/main/kotlin/.../SecureCredentialStore.kt
class SecureCredentialStore @Inject constructor(private val context: Context) {
    // Android Keystore directo
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    fun save(platform: Platform, credentials: PlatformCredentials) {
        val key = getOrCreateKey(platform.name)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(credentials.toJson().toByteArray())
        // Guarda iv + encrypted en DataStore (no prefs)
    }
    
    fun load(platform: Platform): PlatformCredentials? {
        val key = keyStore.getKey(platform.name, null) as SecretKey
        // Lee iv + encrypted, descifra con Keystore key
    }
}
```

**Flujo de onboarding:**
1. Usuario instala la app
2. App solicita permisos requeridos (RECORD_AUDIO, FOREGROUND_SERVICE_MEDIA_PROJECTION, FOREGROUND_SERVICE_CAMERA, FOREGROUND_SERVICE_MICROPHONE)
3. App detecta Shizuku (opcional) → sugiere instalación para features avanzadas
4. Usuario conecta plataformas (OAuth o manual)
   - **Nota**: YouTube y Facebook requieren aprobación de OAuth scopes; sin aprobación, usuario debe usar stream key manual
5. Usuario configura fuentes (pantalla, cámara, mic)
6. Usuario agrega widgets (URLs)
7. Usuario inicia stream

---

## Sección 8: Integración Shizuku (OPCIONAL)

**Importante:** Shizuku **NO es necesario** para la funcionalidad core del MVP:
- Captura de audio: Usa `AudioPlaybackCapture` (API pública, Android 10+)
- Captura de pantalla: Usa `MediaProjection` (API pública)
- Encoding/Streaming: Usa APIs públicas (MediaCodec, RTMP)

Shizuku es **opcional** y solo se usa para features avanzadas que mejoran la experiencia pero no son requeridas.

### Features con/sin Shizuku

| Feature | Sin Shizuku | Con Shizuku |
|---------|-------------|-------------|
| Captura de audio (juegos) | ✅ `AudioPlaybackCapture` (Android 10+) | ✅ Igual |
| Captura de pantalla | ⚠️ Requiere diálogo `MediaProjection` cada sesión (Android 14+) | ✅ Virtual display sin diálogo |
| Foreground service | ⚠️ Puede ser matado por OEMs agresivos | ✅ Protección contra OEMs |
| Input injection (automatización) | ❌ No disponible | ✅ Disponible |

### Módulo `:core:shizuku` (opcional)

```kotlin
// :core:shizuku/src/main/kotlin/.../ShizukuService.kt (interface pública)
interface ShizukuService {
    fun createVirtualDisplay(width: Int, height: Int, dpi: Int): Surface
    fun setForegroundServiceUnrestricted()
    fun injectInputEvent(event: MotionEvent)
}

// :core:shizuku/src/main/kotlin/.../ShizukuServiceImpl.kt (AIDL stub)
class ShizukuServiceImpl : IShizukuService.Stub() {
    override fun createVirtualDisplay(width: Int, height: Int, dpi: Int): Surface {
        // Ejecutado en Shizuku UserService con uid shell
        // DisplayManager.createVirtualDisplay + CAPTURE_SECURE_VIDEO_OUTPUT
        // Retorna Surface como Parcelable (AIDL lo soporta)
    }
    
    override fun setForegroundServiceUnrestricted() {
        // AppOpsManager.setMode(MODE_ALLOWED) para foreground service
        // Previene que OEMs agresivos (Xiaomi, Samsung, etc.) maten el servicio
    }
    
    override fun injectInputEvent(event: MotionEvent) {
        // InputManager.injectInputEvent para automatización
        // Solo disponible con uid shell (Shizuku)
    }
}
```

**Detección de Shizuku:**

```kotlin
class ShizukuDetector @Inject constructor(private val context: Context) {
    fun isShizukuInstalled(): Boolean { ... }
    fun isShizukuRunning(): Boolean { ... }
    fun hasPermission(): Boolean { ... }
    
    // Si Shizuku no está disponible:
    // - La app funciona normalmente (MVP completo)
    // - Solo se pierden features avanzadas (ver tabla arriba)
    // - Se muestra mensaje sugeriendo instalación de Shizuku en Settings
}
```

**Recomendación:** Shizuku es recomendable para usuarios avanzados que quieren:
- Evitar el diálogo de MediaProjection repetidamente
- Protección contra OEMs agresivos que matan foreground services
- Automatización avanzada (input injection para v1.2+)

Pero **NO es requerido** para el MVP.

---

## Sección 9: UI/UX

**Jetpack Compose + Material 3:**

```kotlin
@Composable
fun MultistreamApp(navController: NavController, viewModel: MainViewModel) {
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen(viewModel) }
        composable("streaming") { StreamingScreen(viewModel) }
        composable("settings") { SettingsScreen(viewModel) }
        composable("widgets") { WidgetsConfigScreen(viewModel) }
    }
}
```

**Responsive (teléfono y tablet):**

```kotlin
@Composable
fun StreamingScreen(viewModel: MainViewModel) {
    val isTablet = LocalConfiguration.current.screenWidthDp > 600
    
    if (isTablet) {
        Row {
            StreamPreview(modifier = Modifier.weight(1f))
            StreamingControls(modifier = Modifier.width(300.dp))
        }
    } else {
        Column {
            StreamPreview(modifier = Modifier.weight(1f))
            StreamingControls(modifier = Modifier.height(200.dp))
        }
    }
}
```

**Pantallas principales:**
1. **Home**: Plataformas conectadas, estado de Shizuku, botón "Iniciar stream"
2. **Streaming**: Preview, controles, stats (bitrate, FPS, viewers por plataforma)
3. **Settings**: Configuración de plataformas, fuentes, widgets
4. **Widgets config**: Agregar/quitar/reordenar widgets

**Estadísticas de stream:**

```kotlin
// :data:platforms/src/main/kotlin/.../ViewerCountCollector.kt
class ViewerCountCollector {
    suspend fun getViewerCount(platform: Platform): Int {
        return when (platform) {
            Platform.TWITCH -> fetchFromHelixAPI()  // GET /streams?user_id=...
            Platform.YOUTUBE -> fetchFromLiveChatAPI()  // liveBroadcasts.list?part=snippet,liveStreamingDetails
            Platform.KICK -> fetchFromPublicAPI()  // GET /api/v1/channels/{slug}
            Platform.FACEBOOK -> fetchFromGraphAPI()  // GET /{live-video-id}?fields=live_views
        }
    }
}

// :feature:streaming-ui/src/main/kotlin/.../StreamStats.kt
data class StreamStats(
    val bitrate: Float,              // Desde RTMPMultiStreamer
    val fps: Int,                    // Desde VideoEncoder (frame counter)
    val droppedFrames: Int,          // Desde RTMPMultiStreamer (per connection)
    val viewersByPlatform: Map<Platform, Int>  // Desde ViewerCountCollector
)

// Polling cada 30 segundos (YouTube tiene cuota limitada: 1 unit per call)
// Si una plataforma falla, continúa con las demás
// Cachea resultados por 30s para evitar rate limits
```

**Fuentes de datos:**
- **Bitrate/FPS/dropped frames**: Métricas internas de `:core:streaming` y `:core:encoding`
- **Viewers**: APIs de cada plataforma (Twitch Helix, YouTube Live, Kick public API, Facebook Graph)
- **YouTube cuota**: Cada llamada consume 1 unit. Con polling cada 30s = 120 calls/hora = 120 units. Límite diario de YouTube: 10,000 units por defecto (suficiente para streams de 83 horas)

**Fallback**: Si una API de plataforma falla (rate limit, auth expirado), muestra "—" para esa plataforma y continúa con las demás.

---

## Sección 10: Testing strategy

**Spikes obligatorios pre-implementación:**

Los siguientes spikes deben completarse **antes** de iniciar el plan de implementación:

1. **RTMPS con rtmp-client** (1 día)
   - Objetivo: Validar que `rtmp-client` soporta RTMPS (TLS) en puerto 443
   - Criterio de pase: Handshake exitoso contra `rtmps://live-api-s.facebook.com:443/rtmp/` con stream key de prueba Y validación del endpoint RTMPS de Kick (verificar URL actualizada en Kick dashboard)
   - Si falla: Evaluar wrapper TLS propio, fork de `rtmp-client`, o librería alternativa (`librtmp` + OpenSSL)
   - **Riesgo bloqueante**: Facebook no es alcanzable sin RTMPS

2. **AudioPlaybackCapture en Android 16** (1 día)
   - Objetivo: Validar que captura audio de juegos sin silenciar el dispositivo
   - Criterio de pase: Audio de juego suena en altavoces Y se captura simultáneamente en 3 juegos reales (CODM, Genshin Impact, Clash Royale)
   - Dispositivo de prueba: Teléfono del usuario (Android 16, API 36)
   - Si falla: Evaluar alternativas (REMOTE_SUBMIX con workaround, root, o limitación aceptada)

3. **Widgets: WebView → Surface del compositor** (2 días)
   - Objetivo: Validar que WebView puede renderizar directamente al Surface OpenGL sin overlay window
   - Criterio de pase: Widget de chat de Twitch (URL de Streamlabs) renderizado en textura GL y compuesto con video de juego, manteniendo 60fps estables con chat actualizándose continuamente y video en movimiento
   - Medir: CPU usage, GPU usage, tiempo de composición por frame. Si el costo de Bitmap→GL por frame excede 2ms, buscar alternativas (renderizado incremental, cache de regiones estáticas)
   - Plan B si falla: Renderizado nativo con Compose→Canvas para widgets básicos (chat, alertas simples, texto). Widgets complejos (URL-based) quedan fuera del stream en MVP, solo disponibles en preview local
   - **Decisión requerida**: Si Plan B se activa, definir qué widgets URL-based son críticos para el stream vs cuáles pueden esperar a v1.1

**Tests unitarios:**
- `:core:streaming`: Tests de muxing FLV offline con fixtures grabadas
- `:core:audio`: Golden tests de PCM para DSP chain (input → expected output)
- `:core:encoding`: Tests de composición GL con golden-images (ver abajo)

**Tests de integración:**
- Tests de reconexión RTMP con servidor local (mock)
- Tests de captura de pantalla con dispositivo real (device farm)
- Tests de `CircularBuffer` con edge cases: buffer lleno, reconexión tras timeout, keyframe perdido

**Tests de composición GL (golden-images):**
- Renderizar escena de prueba (pantalla + overlay de widgets como textura GL + cámara PiP) en superficie off-screen
- Comparar píxeles resultantes contra imagen de referencia (tolerancia: ΔE < 2.0 en CIE Lab)
- Escenas de prueba: pantalla sola, pantalla + overlay de widgets (textura GL), pantalla + cámara PiP, escena completa
- Ejecutar en device real (no emulador) para validar comportamiento de GPU real

**Tests de rendimiento:**
- Benchmark de composición GL + encoding en SD8 Gen 3 (objetivo: 60fps sostenidos)
- Monitoreo de throttling térmico en streams de 2+ horas
- Validar que política de degradación de bitrate se activa correctamente

**Tests de Shizuku:**
- Validar que `ShizukuServiceImpl` crea virtual display correctamente
- Validar que features avanzadas funcionan con Shizuku
- Requiere device real con Shizuku instalado (no mockeable en CI)

**Tests de plataformas:**
- Validar que OAuth flow funciona para Twitch/YouTube/Facebook
- Validar que RTMP handshake funciona con ingest reales de cada plataforma (test environment)
- Validar que métricas de viewers se obtienen correctamente de cada API

---

## Sección 11: Compatibilidad de versiones Android

**minSdk:** 29 (Android 10) — requerido para AudioPlaybackCapture API
**targetSdk:** 36 (Android 16) — requerido por Google Play desde agosto 2026

**APIs críticas por versión:**
- Android 10+: `AudioPlaybackCapture` disponible (API pública, recomendada)
- Android 14+: `MediaProjection` requiere re-consentimiento por sesión
- Android 15+: Foreground service type `mediaProjection` requerido (sin límite de 6h, apropiado para streaming con MediaProjection)
- Android 16+: `AudioPlaybackCapture` estable, es la única opción viable para captura de audio sin silenciar el dispositivo

**Comportamiento por versión:**
- **Android 16 (dispositivos actuales)**: `AudioPlaybackCapture` es la API estándar para captura de audio
- **Android 14-15**: Re-consentimiento de MediaProjection por sesión, usar `mediaProjection` FGS type
- **Android 10-13**: `AudioPlaybackCapture` funciona, pero requiere MediaProjection (diálogo de consentimiento) y no reproduce el audio capturado simultáneamente en el dispositivo (requiere implementación manual para "escuchar mientras se captura")

**Estrategia de soporte:**
- MVP: Android 10+ (minSdk 29)
- Dispositivos objetivo: Android 16 (API 36)

---

## Sección 12: Manejo de errores por plataforma

**Taxonomía de errores:**

| Error | Causa | Acción | Reintento |
|---|---|---|---|
| `INVALID_STREAM_KEY` | Stream key incorrecta o expirada | Notificar usuario, pedir reconfiguración | No |
| `INGEST_CONGESTION` | Servidor de plataforma saturado | Reducir bitrate 20%, reintentar | Sí (backoff) |
| `CONNECTION_CLOSED` | Plataforma cierra conexión (timeout, ToS) | Reconectar con exponential backoff | Sí (max 5 intentos) |
| `AUTH_EXPIRED` | Token OAuth expiró | Refrescar token silenciosamente | Sí (1 intento) |
| `TOO_FREQUENT` | Rate limit de plataforma | Esperar 60s, reintentar | Sí (1 intento tras espera) |
| `NETWORK_UNAVAILABLE` | Sin conectividad | Pausar stream, notificar usuario | Sí (al detectar red) |
| `RTMP_HANDSHAKE_FAILED` | Fallo en handshake RTMP | Verificar URL/key, notificar usuario | No |
| `FLV_MUX_ERROR` | Error al muxear FLV | Reiniciar conexión, descartar buffer | Sí (1 intento) |

**Fallo parcial:**
- Cada conexión RTMP es independiente
- Fallo de una no afecta a las demás
- Usuario ve status por plataforma: "Twitch: reconectando", "YouTube: live", etc.
- Si todas las plataformas fallan simultáneamente: pausar stream, notificar usuario

**Política de reintentos:**
```kotlin
class RetryPolicy {
    val maxAttempts = 5
    val backoff = ExponentialBackoff(initial = 1_000, max = 30_000)
    val retryableErrors = setOf(
        RTMPError.CONNECTION_CLOSED,
        RTMPError.INGEST_CONGESTION,
        RTMPError.NETWORK_UNAVAILABLE
    )
}
```

---

## Sección 13: Manejo de cambios de resolución/rotación

```kotlin
// :core:encoding/src/main/kotlin/.../ResolutionChangeHandler.kt
class ResolutionChangeHandler(
    private val compositor: VideoCompositor,
    private val encoder: VideoEncoder,
    private val streamer: RTMPMultiStreamer
) {
    fun onResolutionChange(newWidth: Int, newHeight: Int) {
        // 1. Notifica a VideoCompositor para re-layout
        compositor.setLayout(newWidth, newHeight)
        
        // 2. Reinicia MediaCodec con nueva resolución
        // Esto genera nuevos SPS/PPS (sequence headers)
        encoder.reconfigure(width = newWidth, height = newHeight)
        
        // 3. Espera a que encoder emita nuevo SPS/PPS + IDR frame
        val newSequenceHeader = encoder.waitForNextSequenceHeader()
        
        // 4. Propaga nuevos SPS/PPS a todas las conexiones RTMP
        // Cada conexión RTMP tiene su propio muxer FLV y necesita su propia sequence header
        streamer.propagateSequenceHeaderChange(newSequenceHeader)
        
        // 5. Descarta buffer de frames anteriores
        // Nuevo segment desde el IDR frame
    }
}
```

**Escenarios:**
- **Usuario rota dispositivo**: El virtual display suele manejar rotación automáticamente (contenido rota dentro del buffer). No requiere reinicio de encoder.
- **Usuario cambia de juego con diferente resolución**: Compositor detecta cambio, ajusta layout, encoder se reinicia, nuevos SPS/PPS se propagan a todas las conexiones RTMP.
- **Cambio de resolución a mitad del stream**: Cada conexión RTMP recibe nuevos SPS/PPS + reset de timestamps. Frames anteriores al cambio se descartan del buffer.

**Nota**: Si el encoder no emite nuevos SPS/PPS explícitamente, forzar con `MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME` después de `reconfigure()`.

---

## Sección 14: Licencias y attribution

| Dependencia | Licencia | Notes |
|---|---|---|
| rtmp-client | Apache 2.0 | OK para uso comercial |
| Oboe | Apache 2.0 | OK |
| Shizuku API | Apache 2.0 | OK (opcional) |
| Jetpack Compose | Apache 2.0 | OK |
| AndroidX WebKit | Apache 2.0 | OK |
| CameraX | Apache 2.0 | OK |
| ExoPlayer (Media3) | Apache 2.0 | OK |
| Android Keystore | Apache 2.0 | OK (parte del framework) |
| Hilt | Apache 2.0 | OK |
| Ktor | Apache 2.0 | OK |
| kotlinx-coroutines | Apache 2.0 | OK |
| DataStore | Apache 2.0 | OK |

**Atribución requerida:**
- Shizuku: Mención en "About" screen (si se usa)
- Oboe: Mención en "About" screen
- Todos los Apache 2.0: Incluir copia de licencia en APK
- CameraX, ExoPlayer: Mención en "About" screen (opcional pero recomendado)

---

## Roadmap por fases

### MVP (v1.0)
- Multi-stream a 4 plataformas (Twitch, YouTube, Kick, Facebook) simultáneamente
- **Multi-layout simultáneo**: horizontal (1920x1080) para Twitch/YouTube/Kick/Facebook + vertical (1080x1920) preparado para TikTok
- Dos pipelines de encoding independientes (uno por layout)
- Widgets independientes por layout (cada layout tiene su propia configuración)
- Captura multi-source (pantalla via MediaProjection/virtual display, cámara, imagen, video, mic)
- Audio con DSP chain (EQ, compressor, noise gate) + AudioPlaybackCapture
- OAuth + manual auth
- Shizuku opcional (features avanzadas)
- Perfil de encode: H.264 1080p60 (horizontal) + H.264 1080x1920p60 (vertical)

### v1.1
- TikTok integration (si APIs/ToS lo permiten)
- Sistema de escenas tipo OBS
- Variantes de encode por plataforma

### v1.2
- Mic WiFi: usar micrófono de laptop como fuente de audio (RTP con FEC/jitter buffer)
- RTMP server: app puede recibir streams de otros dispositivos

---

## Riesgos documentados

1. **YouTube/Facebook OAuth**: Lead time de semanas para aprobación de scopes. Sin aprobación, usuario debe usar stream key manual.
2. **TikTok**: APIs no oficiales, riesgo de ban. Fuera del MVP hasta que se estabilice.
3. **Throttling térmico**: SD8 Gen 3 puede sostener 1080p60, pero después de 30-60 min puede haber throttling. Política de degradación de bitrate automática.
4. **Red móvil**: 4-6 Mbps por plataforma × 4 = 16-24 Mbps uplink. Poco realista en LTE/5G mediocre. Requiere WiFi estable o reducción de bitrate.
5. **Apps con DRM**: Algunas apps (Netflix, banking, juegos con DRM) no permiten captura de audio/video con AudioPlaybackCapture. Aceptado para el caso de uso de gaming.
6. **RTMPS no verificado**: `rtmp-client` no tiene soporte RTMPS confirmado. **Spike de 1 día obligatorio** antes de implementación. Si falla: wrapper TLS propio o fork de la lib. Facebook podría no ser alcanzable sin RTMPS.

---

## ⚠️ Spikes obligatorios antes del plan de implementación

**IMPORTANTE**: Antes de proceder al plan de implementación, se deben completar **3 spikes de validación técnica** detallados en la **Sección 10: Testing strategy** (líneas 724-745):

1. **RTMPS con `rtmp-client`** (1 día) — Validar soporte TLS/443. Riesgo bloqueante para Facebook.
2. **AudioPlaybackCapture en Android 16** (1 día) — Validar captura sin silenciar audio. Feature crítica del MVP.
3. **Widgets → Surface del compositor** (2 días) — Validar renderizado directo a 60fps. Plan B disponible si falla.

**Ver detalles completos, criterios de pase, métricas y planes B en la Sección 10.**

Estos spikes deben completarse **ANTES** de escribir el plan de implementación. Los resultados pueden afectar decisiones arquitectónicas y alcance del MVP.
