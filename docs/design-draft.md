# Multistream Android App — Diseño v2 (Post-Auditoría)

## Contexto del proyecto

App Android para streaming simultáneo a múltiples plataformas (Twitch, YouTube, Kick, Facebook). **TikTok fuera del MVP** por riesgos de ToS y APIs no oficiales. Objetivo: "OBS para Android" con widgets personalizables, alto rendimiento (SD8 Gen 3), y integración profunda con Shizuku para capacidades privilegiadas.

## Decisiones de scope

- **MVP (v1.0)**: Multi-stream 4 plataformas (Twitch, YouTube, Kick, Facebook) + captura multi-source + widgets + Shizuku requerido
- **v1.1**: Multi-view (layouts verticales para TikTok/Shorts/Reels) + sistema de escenas + variantes de encode por plataforma
- **v1.2**: Mic WiFi + RTMP server + TikTok (si APIs se estabilizan)

## Decisiones clave

- **Lenguaje**: Kotlin nativo + Jetpack Compose
- **Arquitectura**: Multi-módulo Gradle pragmático
- **Shizuku**: Opcional con modo degradado en v1.0 (MediaProjection + AudioPlaybackCapture con limitaciones). Shizuku desbloquea la experiencia completa.
- **Widgets**: URL-based (estilo Streamlabs/StreamElements) + nativos donde Streamlabs no cubre
- **Auth**: OAuth híbrido (donde sea fácil) + manual fallback
- **Captura**: Multi-source system (pantalla, cámara, imagen, video, mic)
- **iOS**: Android first, port iOS después

---

## Sección 1: Arquitectura de módulos

```
:app                          → UI principal, navegación, composition root
:core:shizuku                 → Shizuku UserService wrapper, permisos privilegiados, streaming PCM
:core:capture                 → Fuentes de video (pantalla via virtual display, cámara, imagen, video file)
:core:audio                   → Remote submix (AudioSystem.setDeviceConnectionState), DSP chain, mezcla
:core:encoding                → MediaCodec, composición de frames + overlays via OpenGL ES
:core:streaming               → RTMP multi-output via rtmp-client, reconexión con política de buffer
:core:widgets                 → WebView widgets, renderizado en overlay window, comunicación
:data:platforms               → Auth, chat, stats por plataforma (Twitch IRC/EventSub, YouTube Live Chat, etc.)
:feature:streaming-ui         → Pantalla de streaming, controles, stats
:feature:settings             → Configuración de plataformas, fuentes, widgets
```

**Cambios vs v1:**
- `:data:platforms` extraído para resolver dependencia circular widgets↔auth
- `:core:streaming` usa `rtmp-client` directamente (no StreamPack multi-output)
- `:core:shizuku` rediseñado alrededor de UserService + streaming PCM

**Flujo de datos principal:**
```
Fuentes (pantalla via virtual display, cámara, mic, audio sistema via remote submix)
    ↓
Composición OpenGL ES (mezcla video + overlays renderizados en overlay window)
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
- **Oboe** — baja latencia para audio
- **Shizuku API** (`dev.rikka.shizuku:api`) — integración con Shizuku
- **Jetpack Compose** + Material 3 — UI
- **AndroidX WebKit** — capa sobre WebView del sistema

---

## Sección 2: Sistema de captura

**Modo primario (con Shizuku):**
Captura del display completo via virtual display con uid shell (sin MediaProjection, sin diálogo).

```kotlin
// :core:capture/src/main/kotlin/.../DisplayCapture.kt
interface DisplayCapture {
    fun createVirtualDisplay(width: Int, height: Int, dpi: Int): Surface
    fun release()
}

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

**Modo degradado (sin Shizuku):**
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

**Fuentes de video adicionales:**

| Fuente | Implementación | Notas |
|---|---|---|
| **Cámara frontal/trasera** | `CameraX` / `Camera2` | Soporte para múltiples cámaras simultáneas |
| **Imagen estática** | `Bitmap` → `Surface` | Para logos, overlays estáticos |
| **Video file** | `MediaPlayer` / `ExoPlayer` → `Surface` | Para intros, transiciones, videos pregrabados |

**Limitaciones documentadas:**
- Apps con `FLAG_SECURE` (Netflix, banking apps) aparecen negro en captura — aceptable para gaming
- No hay captura selectiva por-app (eso requeriría work interno de system_server no disponible)

---

## Sección 3: Sistema de audio

**API de captura: AudioPlaybackCapture (Android 10+, verificado en Android 16)**

**⚠️ CRÍTICO**: `REMOTE_SUBMIX` está roto en Android 16 (scrcpy issue #5960: `UnsupportedOperationException: Cannot create`). Además, `REMOTE_SUBMIX` silencia el audio en el dispositivo, lo cual es inaceptable para gaming.

**Solución verificada**: Usar `AudioPlaybackCapture` API (Android 10+), que:
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
            val buffer = ShortArray(1024)
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
- Virtual displays avanzados
- Background service protection
- Input injection (v1.2+)

**Fuentes de audio adicionales:**

| Fuente | Implementación | Notas |
|---|---|---|
| **Micrófono del dispositivo** | `AudioRecord` | Con DSP chain opcional |
| **Audio del sistema** | Remote submix (Shizuku) o AudioPlaybackCapture (fallback) | Captura completa vs limitada |
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
Audio sistema (remote submix via Shizuku) → ───────────┤→ Mezclador → AAC encoding → RTMP
Alertas (generadas localmente) → ──────────────────────┘
(v1.2) Mic WiFi (RTP) → ───────────────────────────────┘
```

**Oboe para baja latencia:**
Oboe (C++ library con wrapper Kotlin) para procesamiento de audio en tiempo real. Latencia <10ms. Callbacks en C++ para evitar overhead de JNI en hot path.

---

## Sección 4: Sistema de encoding y composición

**Composición OpenGL ES:**

```kotlin
// :core:encoding/src/main/kotlin/.../VideoCompositor.kt
class VideoCompositor {
    fun compose(sources: List<VideoSource>, overlays: List<Overlay>): Surface {
        // OpenGL ES para mezclar:
        // - Fuentes de video (pantalla via virtual display, cámara)
        // - Overlays (widgets renderizados en overlay window, capturados via SurfaceTexture)
        // - Imágenes estáticas
        // Output: Surface que alimenta al MediaCodec encoder
    }
}
```

**Renderizado de widgets (resuelto):**
Widgets no se renderizan directamente en OpenGL. Se usa un **overlay window** que se presenta como un virtual display secundario, y ese display se captura con `SurfaceTexture` → textura GL → composición.

```kotlin
// :core:widgets/src/main/kotlin/.../WidgetOverlayWindow.kt
class WidgetOverlayWindow {
    fun show(widgets: List<Widget>) {
        // Crea un overlay window (TYPE_APPLICATION_OVERLAY)
        // Widgets (WebViews) se renderizan en este window
        // Window se presenta en un virtual display secundario
        // Surface de ese display se captura como textura GL
    }
}
```

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
        // - FPS real vs objetivo
        // - Temperatura de CPU/GPU
        // - Bitrate de red
        // Si throttling térmico: reduce bitrate automáticamente
        // Si red lenta: reduce bitrate o drop frames (no keyframes)
    }
}
```

**Objetivos de rendimiento (SD8 Gen 3):**
- 1 composición + 1 encoder: 1080p60 sostenido
- 2 composiciones + 2 encoders (v1.1 multi-view): 1080p60 + 1080x1920p60, con monitoreo térmico
- Overhead de composición GL: <15% GPU, <10% CPU
- Throttling térmico esperado después de 30-60 min de stream continuo

---

## Sección 5: Sistema de streaming RTMP

**Multi-output RTMP (con rtmp-client):**

```kotlin
// :core:streaming/src/main/kotlin/.../RTMPMultiStreamer.kt
class RTMPMultiStreamer @Inject constructor() {
    private val connections = mutableListOf<RTMPConnection>()
    
    suspend fun connect(configs: List<PlatformConfig>) {
        // Cada conexión es independiente
        // Fallo de una no afecta a las demás
        for (config in configs) {
            val conn = RTMPConnection(config)
            connections.add(conn)
            conn.connectAsync()  // No bloquea
        }
    }
    
    fun send(chunk: EncodedChunk) {
        // Envía el mismo chunk encoded a todas las conexiones activas
        // Cada conexión hace su propio muxing FLV + timestamps
        for (conn in connections) {
            if (conn.isConnected) {
                conn.sendChunk(chunk)
            }
        }
    }
}
```

**Configuración de plataformas (perfil único):**

```kotlin
data class PlatformConfig(
    val platform: Platform,  // TWITCH, YOUTUBE, KICK, FACEBOOK, CUSTOM_RTMP
    val rtmpUrl: String,     // rtmps:// para Facebook/Kick
    val streamKey: String,
    val bitrate: Int         // Compartido por todas las plataformas (v1.0)
    // Nota: resolution, fps, codec, bitrate son compartidos.
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
- **Kick**: H.264, soporta RTMPS
- **Facebook**: RTMPS requerido (`rtmps://live-api-s.facebook.com/rtmp/`)

---

## Sección 6: Sistema de widgets

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

**Renderizado de widgets sobre video (resuelto):**
Widgets se renderizan en un **overlay window** (TYPE_APPLICATION_OVERLAY) que se presenta en un virtual display secundario. Ese display se captura con `SurfaceTexture` → textura GL → composición con el video principal.

**Permisos requeridos:**
La app solicita explícitamente todos los permisos necesarios durante el onboarding, explicando qué funcionalidad habilita cada uno:

| Permiso | Funcionalidad | Requerido |
|---|---|---|
| `RECORD_AUDIO` | Captura de micrófono para stream | Sí |
| `CAMERA` | Cámara frontal/trasera como fuente | Opcional (si usa cámara) |
| `FOREGROUND_SERVICE` | Mantener stream activo en background | Sí |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Captura de pantalla (modo degradado) | Sí si sin Shizuku |
| `FOREGROUND_SERVICE_CAMERA` | Cámara en background | Sí si usa cámara |
| `FOREGROUND_SERVICE_MICROPHONE` | Micrófono en background | Sí |
| `SYSTEM_ALERT_WINDOW` | Overlay para widgets sobre video | Sí para widgets URL |
| `POST_NOTIFICATIONS` | Notificaciones de estado de stream | Opcional |
| `INTERNET` | Streaming RTMP a plataformas | Sí |
| `ACCESS_NETWORK_STATE` | Monitoreo de red para adapt bitrate | Sí |
| `WAKE_LOCK` | Evitar sleep durante stream | Sí |
| `CAPTURE_VIDEO_OUTPUT` | Captura de display (con Shizuku) | Sí con Shizuku |
| `CAPTURE_AUDIO_OUTPUT` | Captura de audio del sistema (con Shizuku) | Sí con Shizuku |
| `MODIFY_AUDIO_ROUTING` | Remote submix para audio del sistema (con Shizuku) | Sí con Shizuku |

**Flujo de solicitud de permisos:**
1. App detecta Shizuku → solicita permisos Shizuku (CAPTURE_VIDEO_OUTPUT, CAPTURE_AUDIO_OUTPUT, MODIFY_AUDIO_ROUTING)
2. App solicita RECORD_AUDIO + FOREGROUND_SERVICE_MEDIA_PROJECTION/CAMERA/MICROPHONE
3. App solicita SYSTEM_ALERT_WINDOW con explicación: "Permite mostrar widgets (chat, alertas) sobre el video del juego"
4. Si usuario deniega SYSTEM_ALERT_WINDOW: widgets inline en preview (no se componen en stream)
5. Si usuario deniega CAMERA: función de cámara deshabilitada (resto funciona)
6. Si usuario deniega RECORD_AUDIO: stream sin micrófono (solo audio del juego)

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
    // Android Keystore directo (EncryptedSharedPreferences está deprecated desde androidx.security 1.1.0)
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
2. App detecta Shizuku → guía para configurarlo (wireless ADB)
3. Usuario conecta plataformas (OAuth o manual)
   - **Nota**: YouTube y Facebook requieren aprobación de OAuth scopes; sin aprobación, usuario debe usar stream key manual
4. Usuario configura fuentes (pantalla, cámara, mic)
5. Usuario agrega widgets (URLs)
6. Usuario inicia stream

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
            Platform.FACEBOOK -> fetchFromGraphAPI()  # GET /{live-video-id}?fields=live_views
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

**Tests unitarios:**
- `:core:streaming`: Tests de muxing FLV offline con fixtures grabadas
- `:core:audio`: Golden tests de PCM para DSP chain (input → expected output)
- `:core:encoding`: Tests de composición GL con golden-images (ver abajo)

**Tests de integración:**
- Tests de reconexión RTMP con servidor local (mock)
- Tests de captura de pantalla con dispositivo real (device farm)
- Tests de `CircularBuffer` con edge cases: buffer lleno, reconexión tras timeout, keyframe perdido

**Tests de composición GL (golden-images):**
- Renderizar escena de prueba (pantalla + overlay + cámara PiP) en superficie off-screen
- Comparar píxeles resultantes contra imagen de referencia (tolerancia: ΔE < 2.0 en CIE Lab)
- Escenas de prueba: pantalla sola, pantalla + overlay, pantalla + cámara PiP, escena completa
- Ejecutar en device real (no emulador) para validar comportamiento de GPU real

**Tests de rendimiento:**
- Benchmark de composición GL + encoding en SD8 Gen 3 (objetivo: 60fps sostenidos)
- Monitoreo de throttling térmico en streams de 2+ horas
- Validar que política de degradación de bitrate se activa correctamente

**Tests de Shizuku:**
- Validar que `ShizukuServiceImpl` crea virtual display correctamente
- Validar que `streamAudioPcm` entrega chunks PCM vía callback
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
- Android 11+: `REMOTE_SUBMIX` funciona (pero silencia audio del dispositivo)
- Android 12+: `REMOTE_SUBMIX` funciona out-of-the-box
- Android 13+: `AudioPlaybackCapture` con `--audio-dup` (modo duplicado)
- Android 14+: `MediaProjection` requiere re-consentimiento por sesión
- Android 15+: Foreground service type `mediaProcessing` introducido (límite 6h)
- Android 16+: `REMOTE_SUBMIX` roto (scrcpy issue #5960), usar `AudioPlaybackCapture`

**Comportamiento por versión:**
- **Android 16 (tu dispositivo)**: `AudioPlaybackCapture` es la única opción viable para captura de audio sin silenciar el dispositivo
- **Android 14-15**: Re-consentimiento de MediaProjection por sesión, usar `mediaProcessing` FGS type
- **Android 10-13**: `AudioPlaybackCapture` funciona, pero sin modo `dup` nativo (requiere implementación manual)

**Estrategia de soporte:**
- MVP: Android 13+ (para `AudioPlaybackCapture` estable)
- v1.1: Android 10+ (con limitaciones en Android 10-12)

**Cambios en Shizuku:**
- Shizuku ya NO es necesario para captura de audio (AudioPlaybackCapture es API pública)
- Shizuku solo se usa para: virtual displays avanzados, background service protection, input injection
- `:core:shizuku` se mantiene opcional para features avanzadas

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
    private val streamers: List<RTMPMultiStreamer>
) {
    fun onResolutionChange(newWidth: Int, newHeight: Int) {
        // 1. Notifica a VideoCompositor para re-layout
        compositor.setLayout(newWidth, newHeight)
        
        // 2. Reinicia MediaCodec con nueva resolución
        // Esto genera nuevos SPS/PPS (sequence headers)
        encoder.reconfigure(width = newWidth, height = newHeight)
        
        // 3. Espera a que encoder emita nuevo SPS/PPS + IDR frame
        val newSequenceHeader = encoder.waitForNextSequenceHeader()
        
        // 4. Envía nuevos SPS/PPS a CADA conexión RTMP
        // Cada muxer FLV necesita su propia sequence header
        for (streamer in streamers) {
            streamer.onSequenceHeaderChange(newSequenceHeader)
            // Reinicia timestamps desde 0 para esta conexión
            streamer.resetTimestamps()
        }
        
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
| Shizuku API | Apache 2.0 | OK |
| Jetpack Compose | Apache 2.0 | OK |
| AndroidX WebKit | Apache 2.0 | OK |
| CameraX | Apache 2.0 | OK |
| ExoPlayer (Media3) | Apache 2.0 | OK |
| AndroidX Security Crypto | Apache 2.0 | OK (deprecated, usar Keystore directo) |
| Android Keystore | Apache 2.0 | OK (parte del framework) |

**Atribución requerida:**
- Shizuku: Mención en "About" screen
- Oboe: Mención en "About" screen
- Todos los Apache 2.0: Incluir copia de licencia en APK
- CameraX, ExoPlayer: Mención en "About" screen (opcional pero recomendado)

---

## Roadmap por fases

### MVP (v1.0)
- Multi-stream a 4 plataformas (Twitch, YouTube, Kick, Facebook) simultáneamente
- Captura multi-source (pantalla via virtual display, cámara, imagen, video, mic)
- Audio con DSP chain (EQ, compressor, noise gate) + remote submix
- Widgets URL-based + nativos (chat Kick)
- OAuth + manual auth
- Shizuku requerido (modo degradado opcional)
- Perfil de encode único: H.264 1080p60

### v1.1
- Multi-view: layouts vertical (TikTok/Shorts/Reels) + horizontal simultáneos
- Sistema de escenas tipo OBS (fuentes, transiciones, hotkeys)
- Variantes de encode por plataforma (ej. TikTok con H.265)

### v1.2
- Mic WiFi: usar micrófono de laptop como fuente de audio (RTP con FEC/jitter buffer)
- RTMP server: app puede recibir streams de otros dispositivos
- TikTok: si APIs se estabilizan y ToS lo permite

---

## Riesgos documentados

1. **YouTube/Facebook OAuth**: Lead time de semanas para aprobación de scopes. Sin aprobación, usuario debe usar stream key manual.
2. **TikTok**: APIs no oficiales, riesgo de ban. Fuera del MVP hasta que se estabilice.
3. **Throttling térmico**: SD8 Gen 3 puede sostener 1080p60, pero después de 30-60 min puede haber throttling. Política de degradación de bitrate automática.
4. **Red móvil**: 4-6 Mbps por plataforma × 4 = 16-24 Mbps uplink. Poco realista en LTE/5G mediocre. Requiere WiFi estable o reducción de bitrate.
5. **Shizuku no disponible**: Modo degradado con limitaciones (diálogo MediaProjection, audio limitado).
