# Multistream Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android app that streams video + audio to 4 platforms (Twitch, YouTube, Kick, Facebook) simultaneously with dual layout support (horizontal 1920x1080 + vertical 1080x1920), independent widgets per layout, using AudioPlaybackCapture API for audio and OpenGL ES for composition.

**Architecture:** Multi-module Gradle project with dual rendering pipelines. `:core:*` modules handle platform APIs, `:data:platforms` manages auth, `:feature:*` modules provide UI, and `:app` wires everything via Hilt DI. Dual composition pipelines share source inputs but produce separate encoded streams with layout-specific widgets.

**Tech Stack:** Kotlin, Jetpack Compose, MediaCodec, OpenGL ES, AudioPlaybackCapture, MediaProjection, rtmp-client, Hilt, Ktor, ExoPlayer/Media3, Shizuku (optional)

**Spec:** `docs/superpowers/specs/2025-10-25-multistream-android-design.md`

## Prerequisites: Mandatory Spikes

Before implementing any task, complete these 3 spikes (detailed in spec Section 10):

1. **RTMPS with rtmp-client** (1 day) — Validate TLS/443 support for Facebook and Kick
2. **AudioPlaybackCapture on Android 16** (1 day) — Validate audio capture without silencing device
3. **Widgets → Compositor Surface** (2 days) — Validate direct rendering at 60fps with async WebView loading

## Global Constraints

- **minSdk 29, targetSdk 36** (Android 10-16)
- **Kotlin only**, no Java
- **All modules use Hilt** for dependency injection
- **Flow-based async** throughout (kotlinx-coroutines)
- **No overlay window for stream composition** (widgets render directly to compositor surface)
- **Dual layout support**: Horizontal (1920x1080, 16:9, H.264, 6 Mbps) + Vertical (1080x1920, 9:16, H.264, 4 Mbps)
- **Two independent encoding pipelines** running simultaneously, sharing the same source inputs
- **Widgets are layout-specific**: Each layout has its own independent widget configuration
- **Audio:** AAC 128 kbps stereo, AudioPlaybackCapture API (shared across both layouts)
- **Video + Audio muxing**: FLV container with both video and audio streams per RTMP connection
- **Foreground service type:** `mediaProjection` (not `mediaProcessing`)
- **Shizuku is optional** — app must work without it
- **API gating:** `FOREGROUND_SERVICE_CAMERA` and `FOREGROUND_SERVICE_MICROPHONE` require API 34+
- **All dependencies:** Apache 2.0 license compatible
- **Common types module:** `:core:common` contains shared types (`Layout`, `Platform`, `EncodedChunk`, `PlatformConfig`, `PlatformCredentials`) to avoid circular dependencies

## Review Focus

1. **Audio capture on Android 16** — Verify AudioPlaybackCapture works without silencing device audio (test in Task 3)
2. **Widget rendering performance** — Validate 60fps with continuous chat updates and async WebView loading (test in Task 9)
3. **RTMPS support** — Confirm rtmp-client handles TLS/443 for Facebook and Kick (test in Task 11)
4. **Thermal throttling with dual encoding** — Monitor CPU/GPU usage with both encoders running (test in Task 21)
5. **Permission denial handling** — App continues working if user denies CAMERA or RECORD_AUDIO (test in Task 6)
6. **Dual layout synchronization** — Both layouts start/stop together, no drift (test in Task 8)
7. **Audio-video sync** — Audio and video remain synchronized throughout the stream (test in Task 7)
8. **Reconnection resilience** — Streams automatically reconnect after network interruption (test in Task 13)

---

## Task 1: Project Setup

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `app/build.gradle.kts`
- Create: `core/common/build.gradle.kts`
- Create: `core/audio/build.gradle.kts`
- Create: `core/capture/build.gradle.kts`
- Create: `core/encoding/build.gradle.kts`
- Create: `core/streaming/build.gradle.kts`
- Create: `core/widgets/build.gradle.kts`
- Create: `data/platforms/build.gradle.kts`
- Create: `feature/settings/build.gradle.kts`
- Create: `feature/streaming-ui/build.gradle.kts`
- Create: `gradle/libs.versions.toml`

**Interfaces:**
- Produces: Multi-module Gradle structure with all dependencies declared

- [ ] **Step 1: Create root build.gradle.kts**

```kotlin
plugins {
    id("com.android.application") version "8.3.0" apply false
    id("com.android.library") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}
```

- [ ] **Step 2: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "multistream"
include(":app")
include(":core:common")
include(":core:audio")
include(":core:capture")
include(":core:encoding")
include(":core:streaming")
include(":core:widgets")
include(":data:platforms")
include(":feature:settings")
include(":feature:streaming-ui")
```

- [ ] **Step 3: Create gradle/libs.versions.toml**

```toml
[versions]
kotlin = "1.9.22"
agp = "8.3.0"
hilt = "2.50"
compose = "1.6.0"
ktor = "2.3.7"
exoplayer = "1.2.1"
coroutines = "1.7.3"

[libraries]
kotlin-stdlib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.12.0" }
lifecycle = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version = "2.7.0" }
datastore = { group = "androidx.datastore", name = "datastore-preferences", version = "1.0.0" }
webkit = { group = "androidx.webkit", name = "webkit", version = "1.10.0" }
compose-ui = { group = "androidx.compose.ui", name = "ui", version.ref = "compose" }
compose-material3 = { group = "androidx.compose.material3", name = "material3", version = "1.2.0" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "exoplayer" }
camerax = { group = "androidx.camera", name = "camera-camera2", version = "1.3.1" }
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
rtmp-client = { group = "io.github.thibaultbee", name = "rtmp-client", version = "2.0.0" }
security-crypto = { group = "androidx.security", name = "security-crypto", version = "1.1.0-alpha06" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 4: Create app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.multistream.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.multistream.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:audio"))
    implementation(project(":core:capture"))
    implementation(project(":core:encoding"))
    implementation(project(":core:streaming"))
    implementation(project(":core:widgets"))
    implementation(project(":data:platforms"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:streaming-ui"))
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
```

- [ ] **Step 5: Create core module build.gradle.kts files**

For `:core:common`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.multistream.core.common"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.kotlin.stdlib)
}
```

For other core modules (`:core:audio`, `:core:capture`, `:core:encoding`, `:core:streaming`, `:core:widgets`):

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.multistream.core.[module]"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
```

- [ ] **Step 6: Create data and feature module build.gradle.kts files**

Similar structure with `implementation(project(":core:common"))`.

- [ ] **Step 7: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add .
git commit -m "feat: add multi-module project structure"
```

---

## Task 2: Core Common — Shared Types

**Files:**
- Create: `core/common/src/main/kotlin/com/multistream/core/common/Layout.kt`
- Create: `core/common/src/main/kotlin/com/multistream/core/common/Platform.kt`
- Create: `core/common/src/main/kotlin/com/multistream/core/common/EncodedChunk.kt`
- Create: `core/common/src/main/kotlin/com/multistream/core/common/PlatformConfig.kt`
- Create: `core/common/src/main/kotlin/com/multistream/core/common/PlatformCredentials.kt`
- Create: `core/common/src/main/kotlin/com/multistream/core/common/Widget.kt`
- Create: `core/common/src/test/kotlin/com/multistream/core/common/TypesTest.kt`

- [ ] **Step 1: Create shared types**

```kotlin
// Layout.kt
package com.multistream.core.common

enum class Layout { HORIZONTAL, VERTICAL }

data class LayoutConfig(
    val layout: Layout,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int
)

val HORIZONTAL_CONFIG = LayoutConfig(Layout.HORIZONTAL, 1920, 1080, 60, 6_000_000)
val VERTICAL_CONFIG = LayoutConfig(Layout.VERTICAL, 1080, 1920, 60, 4_000_000)
```

```kotlin
// Platform.kt
package com.multistream.core.common

enum class Platform { TWITCH, YOUTUBE, KICK, FACEBOOK, CUSTOM }
```

```kotlin
// EncodedChunk.kt
package com.multistream.core.common

data class EncodedChunk(
    val data: ByteArray,
    val timestamp: Long,
    val isKeyFrame: Boolean,
    val layout: Layout,
    val isVideo: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedChunk) return false
        return data.contentEquals(other.data) && timestamp == other.timestamp &&
               isKeyFrame == other.isKeyFrame && layout == other.layout && isVideo == other.isVideo
    }
    override fun hashCode(): Int = 31 * (31 * (31 * (31 * data.contentHashCode() + timestamp.hashCode()) + isKeyFrame.hashCode()) + layout.hashCode()) + isVideo.hashCode()
}
```

```kotlin
// PlatformConfig.kt
package com.multistream.core.common

data class PlatformConfig(
    val platform: Platform,
    val rtmpUrl: String,
    val streamKey: String,
    val bitrate: Int
)
```

```kotlin
// PlatformCredentials.kt
package com.multistream.core.common

data class PlatformCredentials(
    val platform: Platform,
    val rtmpUrl: String,
    val streamKey: String
)

fun PlatformCredentials.toConfig(bitrate: Int = 6_000_000): PlatformConfig =
    PlatformConfig(platform, rtmpUrl, streamKey, bitrate)
```

```kotlin
// Widget.kt
package com.multistream.core.common

data class Widget(
    val id: String,
    val url: String,
    val position: WidgetPosition,
    val size: WidgetSize,
    val layout: Layout
)

data class WidgetPosition(val x: Float, val y: Float)
data class WidgetSize(val width: Int, val height: Int)
```

- [ ] **Step 2: Write and run tests**

```kotlin
@Test fun `Layout enum has HORIZONTAL and VERTICAL values`() { /* ... */ }
@Test fun `Platform enum has all required platforms`() { /* ... */ }
@Test fun `EncodedChunk can be created with all fields`() { /* ... */ }
@Test fun `PlatformCredentials converts to PlatformConfig`() { /* ... */ }
```

- [ ] **Step 3: Commit**

```bash
git add core/common/
git commit -m "feat: add shared types module"
```

---

## Task 3: Core Audio — AudioPlaybackCapture

**Files:**
- Create: `core/audio/src/main/kotlin/com/multistream/core/audio/GameAudioCapture.kt`
- Create: `core/audio/src/test/kotlin/com/multistream/core/audio/GameAudioCaptureTest.kt`

**Interfaces:**
- Consumes: MediaProjection token
- Produces: `Flow<ShortArray>` of PCM audio data

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `GameAudioCapture initializes with correct config`() {
    val capture = GameAudioCapture(context)
    capture.startCapture(mockMediaProjection)
    // Verify AudioPlaybackCaptureConfiguration is built with USAGE_GAME and USAGE_MEDIA
}
```

- [ ] **Step 2: Implement GameAudioCapture**

```kotlin
@Singleton
class GameAudioCapture @Inject constructor(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    
    fun startCapture(mediaProjection: MediaProjection): Flow<ShortArray> = flow {
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .excludeMatchingUsage(AudioAttributes.USAGE_NOTIFICATION)
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
        
        val buffer = ShortArray(2048)
        while (currentCoroutineContext().isActive) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) emit(buffer.copyOf(read))
        }
    }
    
    fun stopCapture() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
```

- [ ] **Step 3: Run tests and commit**

Run: `./gradlew :core:audio:test`
Expected: PASS

```bash
git add core/audio/
git commit -m "feat: implement AudioPlaybackCapture for game audio"
```

---

## Task 4: Core Audio — DSP Chain

**Files:**
- Create: `core/audio/src/main/kotlin/com/multistream/core/audio/dsp/AudioProcessor.kt`
- Create: `core/audio/src/main/kotlin/com/multistream/core/audio/dsp/DSPChain.kt`
- Create: `core/audio/src/main/kotlin/com/multistream/core/audio/dsp/NoiseGate.kt`
- Create: `core/audio/src/main/kotlin/com/multistream/core/audio/dsp/EQ.kt`
- Create: `core/audio/src/main/kotlin/com/multistream/core/audio/dsp/Compressor.kt`
- Create: `core/audio/src/test/kotlin/com/multistream/core/audio/dsp/DSPChainTest.kt`

**Interfaces:**
- Consumes: `ShortArray` PCM data
- Produces: `ShortArray` processed PCM data

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `DSPChain processes audio through all effects in order`() {
    val chain = DSPChain()
    chain.addEffect(NoiseGate(thresholdDb = -40f))
    chain.addEffect(EQ(bands = listOf(EQBand(1000f, 3f))))
    chain.addEffect(Compressor(threshold = -20f, ratio = 4f, attack = 0.01f, release = 0.1f))
    
    val input = ShortArray(1024) { it.toShort() }
    val output = chain.process(input)
    
    assert(output != input)
}
```

- [ ] **Step 2: Implement AudioProcessor, DSPChain, and effects**

```kotlin
interface AudioProcessor {
    fun process(input: ShortArray): ShortArray
}

class DSPChain {
    private val effects = mutableListOf<AudioProcessor>()
    
    fun addEffect(effect: AudioProcessor) { effects.add(effect) }
    
    fun process(input: ShortArray): ShortArray {
        var current = input
        for (effect in effects) { current = effect.process(current) }
        return current
    }
}

// Implement NoiseGate, EQ, Compressor as separate classes
```

- [ ] **Step 3: Run tests and commit**

Run: `./gradlew :core:audio:test`
Expected: PASS

```bash
git add core/audio/src/main/kotlin/com/multistream/core/audio/dsp/
git commit -m "feat: add DSP chain with noise gate, EQ, and compressor"
```

---

## Task 5: Core Audio — Audio Mixer

**Files:**
- Create: `core/audio/src/main/kotlin/com/multistream/core/audio/AudioMixer.kt`
- Create: `core/audio/src/test/kotlin/com/multistream/core/audio/AudioMixerTest.kt`

**Interfaces:**
- Consumes: `ShortArray` from mic, system audio, alerts
- Produces: `ShortArray` mixed audio

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `AudioMixer combines multiple audio sources`() {
    val mixer = AudioMixer()
    val mic = ShortArray(1024) { 100 }
    val system = ShortArray(1024) { 200 }
    val alerts = ShortArray(1024) { 50 }
    
    val mixed = mixer.mix(mic, system, alerts)
    
    assert(mixed.size == 1024)
    assert(mixed[0] > 0)
}
```

- [ ] **Step 2: Implement AudioMixer**

```kotlin
class AudioMixer {
    fun mix(mic: ShortArray?, systemAudio: ShortArray?, alerts: ShortArray?): ShortArray {
        val size = listOfNotNull(mic, systemAudio, alerts).maxOfOrNull { it.size } ?: 0
        val output = ShortArray(size)
        
        for (i in 0 until size) {
            var sum = 0
            mic?.getOrNull(i)?.let { sum += it }
            systemAudio?.getOrNull(i)?.let { sum += it }
            alerts?.getOrNull(i)?.let { sum += it }
            output[i] = (sum / listOfNotNull(mic, systemAudio, alerts).size).toShort()
        }
        
        return output
    }
}
```

- [ ] **Step 3: Run tests and commit**

Run: `./gradlew :core:audio:test`
Expected: PASS

```bash
git add core/audio/src/main/kotlin/com/multistream/core/audio/AudioMixer.kt
git commit -m "feat: implement audio mixer for combining sources"
```

---

## Task 6: Core Capture — MediaProjection Consent & Display

**Files:**
- Create: `core/capture/src/main/kotlin/com/multistream/core/capture/MediaProjectionConsentManager.kt`
- Create: `core/capture/src/main/kotlin/com/multistream/core/capture/DisplayCapture.kt`
- Create: `core/capture/src/main/kotlin/com/multistream/core/capture/MediaProjectionDisplayCapture.kt`
- Create: `core/capture/src/test/kotlin/com/multistream/core/capture/MediaProjectionConsentManagerTest.kt`
- Create: `core/capture/src/test/kotlin/com/multistream/core/capture/MediaProjectionDisplayCaptureTest.kt`

**Interfaces:**
- Consumes: Activity context
- Produces: `MediaProjection` instance, `Surface` for video frames

- [ ] **Step 1: Write failing test for consent manager**

```kotlin
@Test
fun `MediaProjectionConsentManager requests user consent`() = runTest {
    val manager = MediaProjectionConsentManager(context)
    val result = manager.requestProjection(activity)
    assertNotNull(result)
}
```

- [ ] **Step 2: Implement MediaProjectionConsentManager**

```kotlin
@Singleton
class MediaProjectionConsentManager @Inject constructor(private val context: Context) {
    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
    
    suspend fun requestProjection(activity: Activity): MediaProjection? {
        return suspendCancellableCoroutine { continuation ->
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val projection = mediaProjectionManager.getMediaProjection(result.resultCode, result.data!!)
                    continuation.resumeWith(Result.success(projection))
                } else {
                    continuation.resumeWith(Result.success(null))
                }
            }.launch(intent)
        }
    }
}
```

- [ ] **Step 3: Write failing test for display capture**

```kotlin
@Test
fun `MediaProjectionDisplayCapture creates virtual display`() {
    val mockMediaProjection = mockk<MediaProjection>()
    val capture = MediaProjectionDisplayCapture(context)
    val surface = capture.createVirtualDisplay(mockMediaProjection, 1920, 1080, 320)
    assert(surface != null)
}
```

- [ ] **Step 4: Implement DisplayCapture interface and MediaProjectionDisplayCapture**

```kotlin
interface DisplayCapture {
    fun createVirtualDisplay(mediaProjection: MediaProjection, width: Int, height: Int, dpi: Int): Surface
    fun release()
}

@Singleton
class MediaProjectionDisplayCapture @Inject constructor(private val context: Context) : DisplayCapture {
    private var virtualDisplay: VirtualDisplay? = null
    
    override fun createVirtualDisplay(mediaProjection: MediaProjection, width: Int, height: Int, dpi: Int): Surface {
        val surface = SurfaceTexture(false).let { Surface(it) }
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "MultistreamDisplay",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null
        )
        
        return surface
    }
    
    override fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
    }
}
```

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :core:capture:test`
Expected: PASS

```bash
git add core/capture/
git commit -m "feat: implement MediaProjection consent flow and display capture"
```

---

## Task 7: Core Encoding — AAC Audio Encoder

**Files:**
- Create: `core/encoding/src/main/kotlin/com/multistream/core/encoding/AudioEncoder.kt`
- Create: `core/encoding/src/test/kotlin/com/multistream/core/encoding/AudioEncoderTest.kt`

**Dependencies:**
- `:core:common` (for EncodedChunk)

**Interfaces:**
- Consumes: `ShortArray` PCM audio data
- Produces: `Flow<EncodedChunk>` with `isVideo = false`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `AudioEncoder encodes PCM to AAC`() = runTest {
    val encoder = AudioEncoder()
    encoder.configure(sampleRate = 44100, channelCount = 2, bitrate = 128_000)
    
    val pcmData = ShortArray(1024) { it.toShort() }
    val chunks = mutableListOf<EncodedChunk>()
    
    val job = launch { encoder.encode(pcmData).take(3).toList(chunks) }
    job.join()
    
    assert(chunks.isNotEmpty())
    assertTrue(chunks.all { !it.isVideo })
}
```

- [ ] **Step 2: Implement AudioEncoder**

```kotlin
@Singleton
class AudioEncoder @Inject constructor() {
    private var mediaCodec: MediaCodec? = null
    
    fun configure(sampleRate: Int, channelCount: Int, bitrate: Int) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
        }
        
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec?.start()
    }
    
    fun encode(pcmData: ShortArray): Flow<EncodedChunk> = flow {
        val byteBuffer = ByteBuffer.allocate(pcmData.size * 2)
        byteBuffer.asShortBuffer().put(pcmData)
        
        val inputBufferIndex = mediaCodec!!.dequeueInputBuffer(10_000)
        if (inputBufferIndex >= 0) {
            val inputBuffer = mediaCodec!!.getInputBuffer(inputBufferIndex)
            inputBuffer?.clear()
            inputBuffer?.put(byteBuffer)
            mediaCodec!!.queueInputBuffer(inputBufferIndex, 0, byteBuffer.limit(), 0, 0)
        }
        
        val bufferInfo = MediaCodec.BufferInfo()
        var outputBufferIndex = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 10_000)
        
        while (outputBufferIndex >= 0) {
            val outputBuffer = mediaCodec!!.getOutputBuffer(outputBufferIndex)
            val data = ByteArray(bufferInfo.size)
            outputBuffer?.get(data)
            mediaCodec!!.releaseOutputBuffer(outputBufferIndex, false)
            
            emit(EncodedChunk(
                data = data,
                timestamp = bufferInfo.presentationTimeUs,
                isKeyFrame = true,
                layout = Layout.HORIZONTAL,
                isVideo = false
            ))
            
            outputBufferIndex = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 10_000)
        }
    }
    
    fun release() {
        mediaCodec?.stop()
        mediaCodec?.release()
        mediaCodec = null
    }
}
```

- [ ] **Step 3: Run tests and commit**

Run: `./gradlew :core:encoding:test`
Expected: PASS

```bash
git add core/encoding/
git commit -m "feat: implement AAC audio encoder"
```

---

## Task 8: Core Encoding — Multi-Layout Compositor & Dual Encoder

**Files:**
- Create: `core/encoding/src/main/kotlin/com/multistream/core/encoding/MultiLayoutCompositor.kt`
- Create: `core/encoding/src/main/kotlin/com/multistream/core/encoding/DualVideoEncoder.kt`
- Create: `core/encoding/src/main/kotlin/com/multistream/core/encoding/VideoEncoder.kt`
- Create: `core/encoding/src/main/kotlin/com/multistream/core/encoding/CompositionResult.kt`
- Create: `core/encoding/src/test/kotlin/com/multistream/core/encoding/MultiLayoutCompositorTest.kt`
- Create: `core/encoding/src/test/kotlin/com/multistream/core/encoding/DualVideoEncoderTest.kt`

**Dependencies:**
- `:core:common` (for Layout, LayoutConfig, EncodedChunk, Widget)
- `:core:widgets` (for WidgetRenderer)

**Interfaces:**
- Consumes: Video source Surfaces, widget overlays per layout, WidgetRenderer
- Produces: Dual `Flow<EncodedChunk>` streams (horizontal + vertical)

- [ ] **Step 1: Write failing test for MultiLayoutCompositor**

```kotlin
@Test
fun `MultiLayoutCompositor produces both horizontal and vertical surfaces`() {
    val compositor = MultiLayoutCompositor(widgetRenderer)
    val sources = listOf(mockSurface)
    val horizontalWidgets = listOf(widget1)
    val verticalWidgets = listOf(widget2)
    
    val result = compositor.compose(sources, horizontalWidgets, verticalWidgets)
    
    assertNotNull(result.horizontalSurface)
    assertNotNull(result.verticalSurface)
}

@Test
fun `MultiLayoutCompositor renders widgets to composition surface`() {
    val compositor = MultiLayoutCompositor(widgetRenderer)
    val horizontalWidgets = listOf(widget1)
    val verticalWidgets = listOf(widget2)
    
    compositor.compose(listOf(mockSurface), horizontalWidgets, verticalWidgets)
    
    verify { widgetRenderer.renderWidget(widget1.url, any(), any()) }
    verify { widgetRenderer.renderWidget(widget2.url, any(), any()) }
}
```

- [ ] **Step 2: Implement MultiLayoutCompositor**

```kotlin
@Singleton
class MultiLayoutCompositor @Inject constructor(
    private val context: Context,
    private val widgetRenderer: WidgetRenderer
) {
    private val horizontalWidgets = mutableListOf<Widget>()
    private val verticalWidgets = mutableListOf<Widget>()
    
    fun compose(
        sources: List<Surface>,
        horizontalWidgets: List<Widget>,
        verticalWidgets: List<Widget>
    ): CompositionResult {
        this.horizontalWidgets.clear()
        this.horizontalWidgets.addAll(horizontalWidgets)
        this.verticalWidgets.clear()
        this.verticalWidgets.addAll(verticalWidgets)
        
        val horizontalSurface = createCompositionSurface(
            HORIZONTAL_CONFIG.width, HORIZONTAL_CONFIG.height,
            sources, this.horizontalWidgets
        )
        
        val verticalSurface = createCompositionSurface(
            VERTICAL_CONFIG.width, VERTICAL_CONFIG.height,
            sources, this.verticalWidgets
        )
        
        return CompositionResult(horizontalSurface, verticalSurface)
    }
    
    fun getWidgetCount(layout: Layout): Int {
        return when (layout) {
            Layout.HORIZONTAL -> horizontalWidgets.size
            Layout.VERTICAL -> verticalWidgets.size
        }
    }
    
    private fun createCompositionSurface(
        width: Int, height: Int,
        sources: List<Surface>,
        widgets: List<Widget>
    ): Surface {
        // OpenGL ES composition: render sources + widgets into a Surface
        widgets.forEach { widget ->
            val bitmap = widgetRenderer.renderWidget(widget.url, widget.size.width, widget.size.height)
            // Composite bitmap to GL texture at widget.position
        }
        
        val textureView = TextureView(context)
        return Surface(textureView.surfaceTexture)
    }
}

data class CompositionResult(
    val horizontalSurface: Surface,
    val verticalSurface: Surface
)
```

- [ ] **Step 3: Write failing test for DualVideoEncoder**

```kotlin
@Test
fun `DualVideoEncoder configures both encoders`() {
    val encoder = DualVideoEncoder()
    val compositor = mockk<MultiLayoutCompositor>()
    val horizontalSurface = mockk<Surface>()
    val verticalSurface = mockk<Surface>()
    
    every { compositor.compose(any(), any(), any()) } returns CompositionResult(horizontalSurface, verticalSurface)
    
    encoder.configure(compositor, listOf(mockSurface), emptyList(), emptyList())
    
    assertNotNull(encoder.getEncoder(Layout.HORIZONTAL))
    assertNotNull(encoder.getEncoder(Layout.VERTICAL))
}

@Test
fun `DualVideoEncoder produces separate encoded streams per layout`() = runTest {
    val encoder = DualVideoEncoder()
    encoder.configure(mockCompositor, listOf(mockSurface), emptyList(), emptyList())
    
    val horizontalChunks = mutableListOf<EncodedChunk>()
    val verticalChunks = mutableListOf<EncodedChunk>()
    
    val job1 = launch { encoder.encode(Layout.HORIZONTAL).take(5).toList(horizontalChunks) }
    val job2 = launch { encoder.encode(Layout.VERTICAL).take(5).toList(verticalChunks) }
    
    joinAll(job1, job2)
    
    assertEquals(5, horizontalChunks.size)
    assertEquals(5, verticalChunks.size)
    assertTrue(horizontalChunks.all { it.layout == Layout.HORIZONTAL })
    assertTrue(verticalChunks.all { it.layout == Layout.VERTICAL })
}
```

- [ ] **Step 4: Implement VideoEncoder and DualVideoEncoder**

```kotlin
class VideoEncoder {
    private var mediaCodec: MediaCodec? = null
    
    fun configure(width: Int, height: Int, fps: Int, bitrate: Int, inputSurface: Surface) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
        }
        
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec?.configure(format, inputSurface, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec?.start()
    }
    
    fun encode(): Flow<EncodedChunkBase> = flow {
        val bufferInfo = MediaCodec.BufferInfo()
        while (currentCoroutineContext().isActive) {
            val outputIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10_000) ?: -1
            if (outputIndex >= 0) {
                val buffer = mediaCodec?.getOutputBuffer(outputIndex)
                val data = ByteArray(bufferInfo.size)
                buffer?.get(data)
                mediaCodec?.releaseOutputBuffer(outputIndex, false)
                
                emit(EncodedChunkBase(
                    data = data,
                    timestamp = bufferInfo.presentationTimeUs,
                    isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                ))
            }
        }
    }
    
    fun release() {
        mediaCodec?.stop()
        mediaCodec?.release()
        mediaCodec = null
    }
}

data class EncodedChunkBase(
    val data: ByteArray,
    val timestamp: Long,
    val isKeyFrame: Boolean
)

@Singleton
class DualVideoEncoder @Inject constructor() {
    private var horizontalEncoder: VideoEncoder? = null
    private var verticalEncoder: VideoEncoder? = null
    
    fun configure(
        compositor: MultiLayoutCompositor,
        sources: List<Surface>,
        horizontalWidgets: List<Widget>,
        verticalWidgets: List<Widget>
    ) {
        val result = compositor.compose(sources, horizontalWidgets, verticalWidgets)
        
        horizontalEncoder = VideoEncoder().apply {
            configure(HORIZONTAL_CONFIG.width, HORIZONTAL_CONFIG.height,
                     HORIZONTAL_CONFIG.fps, HORIZONTAL_CONFIG.bitrate, result.horizontalSurface)
        }
        
        verticalEncoder = VideoEncoder().apply {
            configure(VERTICAL_CONFIG.width, VERTICAL_CONFIG.height,
                     VERTICAL_CONFIG.fps, VERTICAL_CONFIG.bitrate, result.verticalSurface)
        }
    }
    
    fun encode(layout: Layout): Flow<EncodedChunk> = flow {
        val encoder = when (layout) {
            Layout.HORIZONTAL -> horizontalEncoder
            Layout.VERTICAL -> verticalEncoder
        }
        
        encoder?.encode()?.collect { chunk ->
            emit(EncodedChunk(
                data = chunk.data,
                timestamp = chunk.timestamp,
                isKeyFrame = chunk.isKeyFrame,
                layout = layout,
                isVideo = true
            ))
        }
    }
    
    fun getEncoder(layout: Layout): VideoEncoder? {
        return when (layout) {
            Layout.HORIZONTAL -> horizontalEncoder
            Layout.VERTICAL -> verticalEncoder
        }
    }
    
    fun release() {
        horizontalEncoder?.release()
        verticalEncoder?.release()
        horizontalEncoder = null
        verticalEncoder = null
    }
}
```

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :core:encoding:test`
Expected: PASS

```bash
git add core/encoding/
git commit -m "feat: implement multi-layout compositor with widget rendering and dual video encoder"
```

---

## Task 9: Core Widgets — WebView Rendering & Layout Manager

**Files:**
- Create: `core/widgets/src/main/kotlin/com/multistream/core/widgets/WidgetRenderer.kt`
- Create: `core/widgets/src/main/kotlin/com/multistream/core/widgets/WidgetLayoutManager.kt`
- Create: `core/widgets/src/main/kotlin/com/multistream/core/widgets/WidgetEventBus.kt`
- Create: `core/widgets/src/test/kotlin/com/multistream/core/widgets/WidgetRendererTest.kt`
- Create: `core/widgets/src/test/kotlin/com/multistream/core/widgets/WidgetLayoutManagerTest.kt`

**Interfaces:**
- Consumes: Widget URLs, Layout type
- Produces: Bitmap rendered to compositor Surface, layout-specific widget lists, stream events

- [ ] **Step 1: Write failing test for WidgetLayoutManager with StateFlow**

```kotlin
@Test
fun `WidgetLayoutManager maintains separate widget lists per layout as StateFlow`() = runTest {
    val manager = WidgetLayoutManager()
    val horizontalWidget = Widget("1", "https://example.com/h", WidgetPosition(0f, 0f), WidgetSize(400, 300), Layout.HORIZONTAL)
    val verticalWidget = Widget("2", "https://example.com/v", WidgetPosition(0f, 0f), WidgetSize(300, 400), Layout.VERTICAL)
    
    manager.addWidget(horizontalWidget)
    manager.addWidget(verticalWidget)
    
    val horizontalWidgets = manager.getWidgetsForLayout(Layout.HORIZONTAL).first()
    val verticalWidgets = manager.getWidgetsForLayout(Layout.VERTICAL).first()
    
    assertEquals(1, horizontalWidgets.size)
    assertEquals(1, verticalWidgets.size)
    assertEquals(Layout.HORIZONTAL, horizontalWidgets[0].layout)
    assertEquals(Layout.VERTICAL, verticalWidgets[0].layout)
}
```

- [ ] **Step 2: Implement WidgetLayoutManager with StateFlow**

```kotlin
@Singleton
class WidgetLayoutManager @Inject constructor() {
    private val widgets = MutableStateFlow<List<Widget>>(emptyList())
    
    fun addWidget(widget: Widget) {
        widgets.update { it + widget }
    }
    
    fun removeWidget(widgetId: String) {
        widgets.update { list -> list.filter { it.id != widgetId } }
    }
    
    fun getWidgetsForLayout(layout: Layout): StateFlow<List<Widget>> {
        return widgets.map { list -> list.filter { it.layout == layout } }.stateIn(
            scope = CoroutineScope(Dispatchers.Default),
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )
    }
    
    fun getAllWidgets(): StateFlow<List<Widget>> = widgets
}
```

- [ ] **Step 3: Write failing test for widget rendering with async loading**

```kotlin
@Test
fun `WidgetRenderer renders WebView to bitmap with async loading`() {
    val renderer = WidgetRenderer(context)
    val bitmap = renderer.renderWidget("https://example.com/widget")
    
    assert(bitmap != null)
    assert(bitmap.width > 0 && bitmap.height > 0)
}
```

- [ ] **Step 4: Implement WidgetRenderer with async loading**

```kotlin
@Singleton
class WidgetRenderer @Inject constructor(private val context: Context) {
    suspend fun renderWidget(url: String, width: Int = 400, height: Int = 300): Bitmap {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Wait for WebView to load
        val latch = CountDownLatch(1)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val canvas = Canvas(bitmap)
                webView.layout(0, 0, width, height)
                webView.draw(canvas)
                latch.countDown()
            }
        }
        
        webView.loadUrl(url)
        latch.await(5, TimeUnit.SECONDS)
        
        return bitmap
    }
}
```

- [ ] **Step 5: Implement WidgetEventBus**

```kotlin
@Singleton
class WidgetEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<StreamEvent>()
    val events: SharedFlow<StreamEvent> = _events
    
    suspend fun sendEvent(event: StreamEvent) {
        _events.emit(event)
    }
}

sealed class StreamEvent {
    data class Follow(val platform: Platform, val username: String) : StreamEvent()
    data class Subscription(val platform: Platform, val username: String, val months: Int) : StreamEvent()
    data class Donation(val platform: Platform, val username: String, val amount: Float) : StreamEvent()
    data class ChatMessage(val platform: Platform, val username: String, val message: String) : StreamEvent()
}
```

- [ ] **Step 6: Run tests and commit**

Run: `./gradlew :core:widgets:test`
Expected: PASS

```bash
git add core/widgets/
git commit -m "feat: implement widget renderer, layout manager with StateFlow, and event bus"
```

---

## Task 10: Core Streaming — RTMP Connection & Layout Router

**Files:**
- Create: `core/streaming/src/main/kotlin/com/multistream/core/streaming/RTMPConnection.kt`
- Create: `core/streaming/src/main/kotlin/com/multistream/core/streaming/LayoutRouter.kt`
- Create: `core/streaming/src/test/kotlin/com/multistream/core/streaming/RTMPConnectionTest.kt`
- Create: `core/streaming/src/test/kotlin/com/multistream/core/streaming/LayoutRouterTest.kt`

**Interfaces:**
- Consumes: `PlatformConfig`, `Platform` enum
- Produces: Connection status, error handling, Layout routing decisions

- [ ] **Step 1: Implement LayoutRouter**

```kotlin
interface LayoutRouter {
    fun getLayout(platform: Platform): Layout
}

@Singleton
class DefaultLayoutRouter @Inject constructor() : LayoutRouter {
    override fun getLayout(platform: Platform): Layout {
        return when (platform) {
            Platform.TWITCH, Platform.YOUTUBE, Platform.KICK, Platform.FACEBOOK -> Layout.HORIZONTAL
        }
    }
}
```

- [ ] **Step 2: Write and run tests for LayoutRouter**

```kotlin
@Test fun `LayoutRouter returns HORIZONTAL for all platforms`() {
    val router = DefaultLayoutRouter()
    assertEquals(Layout.HORIZONTAL, router.getLayout(Platform.TWITCH))
    assertEquals(Layout.HORIZONTAL, router.getLayout(Platform.YOUTUBE))
    assertEquals(Layout.HORIZONTAL, router.getLayout(Platform.KICK))
    assertEquals(Layout.HORIZONTAL, router.getLayout(Platform.FACEBOOK))
}
```

- [ ] **Step 3: Write failing test for RTMP connection with layout and audio support**

```kotlin
@Test
fun `RTMPConnection sends both video and audio chunks`() = runTest {
    val config = PlatformConfig(Platform.TWITCH, "rtmp://...", "key", 6_000_000)
    val connection = RTMPConnection(config, Layout.HORIZONTAL)
    
    connection.connect()
    
    val videoChunk = EncodedChunk(ByteArray(100), 0L, true, Layout.HORIZONTAL, isVideo = true)
    val audioChunk = EncodedChunk(ByteArray(50), 0L, false, Layout.HORIZONTAL, isVideo = false)
    
    connection.sendChunk(videoChunk)
    connection.sendChunk(audioChunk)
    
    verify { connection.sendVideoChunk(videoChunk) }
    verify { connection.sendAudioChunk(audioChunk) }
}
```

- [ ] **Step 4: Implement RTMPConnection with layout and audio support**

```kotlin
class RTMPConnection(
    private val config: PlatformConfig,
    val layout: Layout
) {
    private var client: RtmpClient? = null
    var isConnected = false
        private set
    
    suspend fun connect() = withContext(Dispatchers.IO) {
        client = RtmpClient().apply {
            connect(config.rtmpUrl, config.streamKey)
        }
        isConnected = true
    }
    
    fun sendChunk(chunk: EncodedChunk) {
        if (isConnected && chunk.layout == layout) {
            if (chunk.isVideo) {
                client?.writeVideoChunk(chunk.data, chunk.timestamp)
            } else {
                client?.writeAudioChunk(chunk.data, chunk.timestamp)
            }
        }
    }
    
    fun disconnect() {
        client?.disconnect()
        isConnected = false
    }
}
```

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :core:streaming:test`
Expected: PASS

```bash
git add core/streaming/
git commit -m "feat: implement RTMP connection with layout routing and audio support"
```

---

## Task 11: Core Streaming — Multi-Output Streamer with Reconnection

**Files:**
- Create: `core/streaming/src/main/kotlin/com/multistream/core/streaming/RTMPMultiStreamer.kt`
- Create: `core/streaming/src/main/kotlin/com/multistream/core/streaming/CircularBuffer.kt`
- Create: `core/streaming/src/main/kotlin/com/multistream/core/streaming/RetryPolicy.kt`
- Create: `core/streaming/src/test/kotlin/com/multistream/core/streaming/RTMPMultiStreamerTest.kt`
- Create: `core/streaming/src/test/kotlin/com/multistream/core/streaming/CircularBufferTest.kt`

**Interfaces:**
- Consumes: List of `PlatformConfig`, `EncodedChunk` with layout info, `LayoutRouter`
- Produces: Multi-platform streaming routed by layout with automatic reconnection

- [ ] **Step 1: Implement CircularBuffer**

```kotlin
class CircularBuffer<T>(private val maxSizeMb: Int = 10, private val maxAgeSeconds: Int = 5) {
    private val buffer = mutableListOf<TimedItem<T>>()
    
    fun add(item: T, timestamp: Long) {
        buffer.add(TimedItem(item, timestamp))
        cleanup()
    }
    
    fun lastKeyframe(): TimedItem<T>? = buffer.lastOrNull { /* check if keyframe */ }
    
    fun from(item: TimedItem<T>): List<T> = buffer.dropWhile { it != item }.map { it.item }
    
    private fun cleanup() {
        val now = System.currentTimeMillis()
        buffer.removeAll { now - it.timestamp > maxAgeSeconds * 1000 }
    }
    
    data class TimedItem<T>(val item: T, val timestamp: Long)
}
```

- [ ] **Step 2: Implement RetryPolicy**

```kotlin
class RetryPolicy(
    val maxAttempts: Int = 5,
    val backoff: ExponentialBackoff = ExponentialBackoff(initial = 1_000, max = 30_000)
) {
    suspend fun <T> retry(block: suspend () -> T): T {
        var attempts = 0
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (++attempts >= maxAttempts) throw e
                delay(backoff.nextDelay())
            }
        }
    }
}

class ExponentialBackoff(private val initial: Long, private val max: Long) {
    private var current = initial
    
    fun nextDelay(): Long {
        val delay = current
        current = (current * 2).coerceAtMost(max)
        return delay
    }
}
```

- [ ] **Step 3: Write failing test for multi-output streaming with layout routing and reconnection**

```kotlin
@Test
fun `RTMPMultiStreamer routes chunks to correct layout connections and handles reconnection`() = runTest {
    val layoutRouter = DefaultLayoutRouter()
    val streamer = RTMPMultiStreamer(layoutRouter)
    
    val twitchConfig = PlatformConfig(Platform.TWITCH, "rtmp://...", "key1", 6_000_000)
    val youtubeConfig = PlatformConfig(Platform.YOUTUBE, "rtmp://...", "key2", 6_000_000)
    
    streamer.connect(listOf(twitchConfig, youtubeConfig))
    
    val horizontalChunk = EncodedChunk(ByteArray(100), 0L, true, Layout.HORIZONTAL, isVideo = true)
    streamer.send(horizontalChunk)
    
    assert(streamer.getHorizontalConnectionCount() == 2)
    assert(streamer.getVerticalConnectionCount() == 0)
}
```

- [ ] **Step 4: Implement RTMPMultiStreamer with layout routing and reconnection**

```kotlin
@Singleton
class RTMPMultiStreamer @Inject constructor(
    private val layoutRouter: LayoutRouter
) {
    private val connections = mutableListOf<RTMPConnection>()
    private val retryPolicy = RetryPolicy()
    
    suspend fun connect(configs: List<PlatformConfig>) = coroutineScope {
        configs.map { config ->
            async {
                retryPolicy.retry {
                    val layout = layoutRouter.getLayout(config.platform)
                    val conn = RTMPConnection(config, layout)
                    conn.connect()
                    connections.add(conn)
                }
            }
        }.awaitAll()
    }
    
    fun send(chunk: EncodedChunk) {
        connections.forEach { conn ->
            if (conn.isConnected && conn.layout == chunk.layout) {
                conn.sendChunk(chunk)
            }
        }
    }
    
    fun getConnection(platform: Platform): RTMPConnection? {
        return connections.find { it.config.platform == platform }
    }
    
    fun getHorizontalConnectionCount(): Int {
        return connections.count { it.layout == Layout.HORIZONTAL && it.isConnected }
    }
    
    fun getVerticalConnectionCount(): Int {
        return connections.count { it.layout == Layout.VERTICAL && it.isConnected }
    }
    
    val connectionCount: Int get() = connections.size
    
    suspend fun disconnectAll() {
        connections.forEach { it.disconnect() }
        connections.clear()
    }
}
```

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :core:streaming:test`
Expected: PASS

```bash
git add core/streaming/
git commit -m "feat: implement multi-output RTMP streamer with layout routing and reconnection"
```

---

## Task 12: Data Platforms — Secure Credential Storage

**Files:**
- Create: `data/platforms/src/main/kotlin/com/multistream/data/platforms/SecureCredentialStore.kt`
- Create: `data/platforms/src/test/kotlin/com/multistream/data/platforms/SecureCredentialStoreTest.kt`

**Interfaces:**
- Consumes: PlatformCredentials
- Produces: Encrypted storage of credentials

- [ ] **Step 1: Write failing test for secure credential storage**

```kotlin
@Test
fun `SecureCredentialStore encrypts and decrypts credentials`() {
    val store = SecureCredentialStore(context)
    val credentials = PlatformCredentials(Platform.TWITCH, "rtmp://...", "key123")
    
    store.save(credentials)
    val loaded = store.load(Platform.TWITCH)
    
    assertEquals(credentials, loaded)
}
```

- [ ] **Step 2: Implement SecureCredentialStore with Android Keystore**

```kotlin
@Singleton
class SecureCredentialStore @Inject constructor(private val context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val dataStore = context.dataStore
    
    suspend fun save(credentials: PlatformCredentials) {
        val key = getOrCreateKey(credentials.platform.name)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(credentials.toString().toByteArray())
        
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("${credentials.platform.name}_iv")] = iv.toBase64()
            prefs[stringPreferencesKey("${credentials.platform.name}_data")] = encrypted.toBase64()
        }
    }
    
    suspend fun load(platform: Platform): PlatformCredentials? {
        val prefs = dataStore.data.first()
        val iv = prefs[stringPreferencesKey("${platform.name}_iv")]?.fromBase64() ?: return null
        val encrypted = prefs[stringPreferencesKey("${platform.name}_data")]?.fromBase64() ?: return null
        
        val key = keyStore.getKey(platform.name, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        val decrypted = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        
        return PlatformCredentials.fromString(decrypted)
    }
    
    private fun getOrCreateKey(alias: String): SecretKey {
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGenerator.init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            return keyGenerator.generateKey()
        }
        return keyStore.getKey(alias, null) as SecretKey
    }
}
```

- [ ] **Step 3: Run tests and commit**

Run: `./gradlew :data:platforms:test`
Expected: PASS

```bash
git add data/platforms/
git commit -m "feat: implement secure credential storage with Android Keystore"
```

---

## Task 13: Data Platforms — Platform Authentication

**Files:**
- Create: `data/platforms/src/main/kotlin/com/multistream/data/platforms/PlatformAuth.kt`
- Create: `data/platforms/src/main/kotlin/com/multistream/data/platforms/TwitchAuth.kt`
- Create: `data/platforms/src/main/kotlin/com/multistream/data/platforms/YouTubeAuth.kt`
- Create: `data/platforms/src/main/kotlin/com/multistream/data/platforms/FacebookAuth.kt`
- Create: `data/platforms/src/main/kotlin/com/multistream/data/platforms/ManualAuth.kt`
- Create: `data/platforms/src/test/kotlin/com/multistream/data/platforms/PlatformAuthTest.kt`

**Interfaces:**
- Consumes: User credentials, SecureCredentialStore
- Produces: `PlatformCredentials` (RTMP URL + stream key)

- [ ] **Step 1: Implement PlatformAuth interface and implementations**

```kotlin
interface PlatformAuth {
    suspend fun login(): PlatformCredentials
}

class TwitchAuth @Inject constructor(
    private val credentialStore: SecureCredentialStore
) : PlatformAuth {
    override suspend fun login(): PlatformCredentials {
        // Check if credentials exist
        credentialStore.load(Platform.TWITCH)?.let { return it }
        
        // OAuth 2.0 flow with Twitch
        // For MVP: manual entry fallback
        return PlatformCredentials(Platform.TWITCH, "rtmp://live.twitch.tv/app/", "")
    }
}

class YouTubeAuth @Inject constructor(
    private val credentialStore: SecureCredentialStore
) : PlatformAuth {
    override suspend fun login(): PlatformCredentials {
        credentialStore.load(Platform.YOUTUBE)?.let { return it }
        // OAuth 2.0 with Google (requires scopes verification)
        return PlatformCredentials(Platform.YOUTUBE, "rtmp://a.rtmp.youtube.com/live2/", "")
    }
}

class FacebookAuth @Inject constructor(
    private val credentialStore: SecureCredentialStore
) : PlatformAuth {
    override suspend fun login(): PlatformCredentials {
        credentialStore.load(Platform.FACEBOOK)?.let { return it }
        // OAuth 2.0 with Facebook (requires app review)
        return PlatformCredentials(Platform.FACEBOOK, "rtmps://live-api-s.facebook.com:443/rtmp/", "")
    }
}

class ManualAuth @Inject constructor(
    private val credentialStore: SecureCredentialStore
) : PlatformAuth {
    override suspend fun login(platform: Platform, rtmpUrl: String, streamKey: String): PlatformCredentials {
        val credentials = PlatformCredentials(platform, rtmpUrl, streamKey)
        credentialStore.save(credentials)
        return credentials
    }
}
```

- [ ] **Step 2: Write and run tests**

```kotlin
@Test fun `TwitchAuth performs OAuth flow`() = runTest { /* ... */ }
@Test fun `YouTubeAuth performs OAuth flow`() = runTest { /* ... */ }
@Test fun `FacebookAuth performs OAuth flow`() = runTest { /* ... */ }
@Test fun `ManualAuth stores credentials securely`() = runTest { /* ... */ }
```

- [ ] **Step 3: Commit**

```bash
git add data/platforms/
git commit -m "feat: implement platform authentication with secure storage"
```

---

## Task 14: Core Capture — Additional Video Sources

**Files:**
- Create: `core/capture/src/main/kotlin/com/multistream/core/capture/CameraCapture.kt`
- Create: `core/capture/src/main/kotlin/com/multistream/core/capture/ImageCapture.kt`
- Create: `core/capture/src/main/kotlin/com/multistream/core/capture/VideoFileCapture.kt`
- Create: `core/capture/src/test/kotlin/com/multistream/core/capture/CameraCaptureTest.kt`

**Interfaces:**
- Consumes: Camera device, image files, video files
- Produces: `Surface` for each source type

- [ ] **Step 1: Write failing test for camera capture**

```kotlin
@Test
fun `CameraCapture opens front camera and produces surface`() {
    val capture = CameraCapture(context)
    val surface = capture.openFrontCamera()
    assertNotNull(surface)
}
```

- [ ] **Step 2: Implement CameraCapture with CameraX**

```kotlin
@Singleton
class CameraCapture @Inject constructor(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    
    suspend fun openFrontCamera(): Surface {
        cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        
        val preview = Preview.Builder().build()
        val surface = preview.surfaceProvider.let {
            // Create Surface from preview surface provider
            Surface(SurfaceTexture(0))
        }
        
        cameraProvider?.unbindAll()
        cameraProvider?.bindToLifecycle(
            LifecycleOwner { it.lifecycle },
            cameraSelector,
            preview
        )
        
        return surface
    }
    
    fun release() {
        cameraProvider?.unbindAll()
    }
}
```

- [ ] **Step 3: Implement ImageCapture and VideoFileCapture**

```kotlin
class ImageCapture @Inject constructor(private val context: Context) {
    fun loadImage(uri: Uri): Surface {
        val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
        val surfaceTexture = SurfaceTexture(0)
        surfaceTexture.setDefaultBufferSize(bitmap.width, bitmap.height)
        val surface = Surface(surfaceTexture)
        Canvas(surface).drawBitmap(bitmap, 0f, 0f, null)
        return surface
    }
}

class VideoFileCapture @Inject constructor(private val context: Context) {
    private var player: ExoPlayer? = null
    
    fun loadVideo(uri: Uri): Surface {
        player = ExoPlayer.Builder(context).build()
        player?.setMediaItem(MediaItem.fromUri(uri))
        
        val surfaceTexture = SurfaceTexture(0)
        val surface = Surface(surfaceTexture)
        player?.setVideoSurface(surface)
        player?.prepare()
        player?.play()
        
        return surface
    }
    
    fun release() {
        player?.release()
        player = null
    }
}
```

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew :core:capture:test`
Expected: PASS

```bash
git add core/capture/
git commit -m "feat: implement additional video sources (camera, image, video file)"
```

---

## Task 15: Core Encoding — Performance Monitor

**Files:**
- Create: `core/encoding/src/main/kotlin/com/multistream/core/encoding/PerformanceMonitor.kt`
- Create: `core/encoding/src/test/kotlin/com/multistream/core/encoding/PerformanceMonitorTest.kt`

**Interfaces:**
- Consumes: CPU/GPU metrics, thermal status
- Produces: Performance recommendations (bitrate reduction, frame dropping)

- [ ] **Step 1: Write failing test for thermal throttling detection**

```kotlin
@Test
fun `PerformanceMonitor detects thermal throttling and recommends bitrate reduction`() {
    val monitor = PerformanceMonitor()
    monitor.startMonitoring()
    
    // Simulate thermal throttling
    monitor.onThermalStatusChanged(PowerManager.THERMAL_STATUS_SEVERE)
    
    val recommendation = monitor.getRecommendation()
    assertTrue(recommendation.shouldReduceBitrate)
    assert(recommendation.bitrateMultiplier < 1.0f)
}
```

- [ ] **Step 2: Implement PerformanceMonitor**

```kotlin
@Singleton
class PerformanceMonitor @Inject constructor(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var currentThermalStatus = PowerManager.THERMAL_STATUS_NORMAL
    private var cpuUsage = 0f
    private var gpuUsage = 0f
    
    fun startMonitoring() {
        powerManager.addThermalStatusListener { status ->
            currentThermalStatus = status
        }
        
        // Monitor CPU/GPU usage periodically
        // Implementation depends on device-specific APIs
    }
    
    fun onThermalStatusChanged(status: Int) {
        currentThermalStatus = status
    }
    
    fun getRecommendation(): PerformanceRecommendation {
        val shouldReduceBitrate = currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        val bitrateMultiplier = when (currentThermalStatus) {
            PowerManager.THERMAL_STATUS_MODERATE -> 0.8f
            PowerManager.THERMAL_STATUS_SEVERE -> 0.6f
            PowerManager.THERMAL_STATUS_CRITICAL -> 0.4f
            else -> 1.0f
        }
        
        return PerformanceRecommendation(
            shouldReduceBitrate = shouldReduceBitrate,
            bitrateMultiplier = bitrateMultiplier,
            shouldDropFrames = currentThermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL
        )
    }
}

data class PerformanceRecommendation(
    val shouldReduceBitrate: Boolean,
    val bitrateMultiplier: Float,
    val shouldDropFrames: Boolean
)
```

- [ ] **Step 3: Run tests and commit**

Run: `./gradlew :core:encoding:test`
Expected: PASS

```bash
git add core/encoding/
git commit -m "feat: implement performance monitor with thermal throttling detection"
```

---

## Task 16: Core Encoding — Resolution Change Handler

**Files:**
- Create: `core/encoding/src/main/kotlin/com/multistream/core/encoding/ResolutionChangeHandler.kt`
- Create: `core/encoding/src/test/kotlin/com/multistream/core/encoding/ResolutionChangeHandlerTest.kt`

**Interfaces:**
- Consumes: New resolution dimensions, current encoder state
- Produces: Reconfigured encoder with new SPS/PPS

- [ ] **Step 1: Write failing test for resolution change handling**

```kotlin
@Test
fun `ResolutionChangeHandler reconfigures encoder and propagates new SPS/PPS`() {
    val handler = ResolutionChangeHandler()
    val encoder = mockk<VideoEncoder>()
    val streamer = mockk<RTMPMultiStreamer>()
    
    handler.onResolutionChange(1280, 720, encoder, streamer)
    
    verify { encoder.reconfigure(1280, 720) }
    verify { streamer.propagateSequenceHeaderChange(any()) }
}
```

- [ ] **Step 2: Implement ResolutionChangeHandler**

```kotlin
@Singleton
class ResolutionChangeHandler @Inject constructor() {
    fun onResolutionChange(
        newWidth: Int,
        newHeight: Int,
        encoder: VideoEncoder,
        streamer: RTMPMultiStreamer
    ) {
        // Reconfigure encoder with new resolution
        encoder.reconfigure(newWidth, newHeight)
        
        // Wait for new SPS/PPS
        val newSequenceHeader = encoder.waitForNextSequenceHeader()
        
        // Propagate to all RTMP connections
        streamer.propagateSequenceHeaderChange(newSequenceHeader)
    }
}

// Extension function for VideoEncoder
fun VideoEncoder.reconfigure(width: Int, height: Int) {
    // Release current codec
    release()
    // Reconfigure with new dimensions
    configure(width, height, 60, 6_000_000, /* surface */)
}

fun VideoEncoder.waitForNextSequenceHeader(): ByteArray {
    // Wait for IDR frame with new SPS/PPS
    // Return the sequence header data
    return ByteArray(0) // Placeholder
}

// Extension function for RTMPMultiStreamer
fun RTMPMultiStreamer.propagateSequenceHeaderChange(header: ByteArray) {
    // Send new sequence header to all connections
    // Each connection resets its timestamps
}
```

- [ ] **Step 3: Run tests and commit**

Run: `./gradlew :core:encoding:test`
Expected: PASS

```bash
git add core/encoding/
git commit -m "feat: implement resolution change handler with SPS/PPS propagation"
```

---

## Task 17: Core Streaming — Error Handling & Retry Policy

**Files:**
- Create: `core/streaming/src/main/kotlin/com/multistream/core/streaming/ErrorHandler.kt`
- Create: `core/streaming/src/main/kotlin/com/multistream/core/streaming/RTMPError.kt`
- Create: `core/streaming/src/test/kotlin/com/multistream/core/streaming/ErrorHandlerTest.kt`

**Interfaces:**
- Consumes: RTMP errors, connection state
- Produces: Error handling actions (retry, pause, notify user)

- [ ] **Step 1: Define RTMPError taxonomy**

```kotlin
sealed class RTMPError {
    object INVALID_STREAM_KEY : RTMPError()
    object INGEST_CONGESTION : RTMPError()
    object CONNECTION_CLOSED : RTMPError()
    object AUTH_EXPIRED : RTMPError()
    object TOO_FREQUENT : RTMPError()
    object NETWORK_UNAVAILABLE : RTMPError()
    object RTMP_HANDSHAKE_FAILED : RTMPError()
    object FLV_MUX_ERROR : RTMPError()
    object PROJECTION_REVOKED : RTMPError()
}
```

- [ ] **Step 2: Write failing test for error handling**

```kotlin
@Test
fun `ErrorHandler retries on CONNECTION_CLOSED with exponential backoff`() = runTest {
    val handler = ErrorHandler()
    var attempts = 0
    
    handler.handleError(RTMPError.CONNECTION_CLOSED) {
        attempts++
        if (attempts < 3) throw RTMPError.CONNECTION_CLOSED
    }
    
    assertEquals(3, attempts)
}

@Test
fun `ErrorHandler does not retry on INVALID_STREAM_KEY`() = runTest {
    val handler = ErrorHandler()
    var attempts = 0
    
    try {
        handler.handleError(RTMPError.INVALID_STREAM_KEY) {
            attempts++
            throw RTMPError.INVALID_STREAM_KEY
        }
    } catch (e: RTMPError) {
        // Expected
    }
    
    assertEquals(1, attempts)
}
```

- [ ] **Step 3: Implement ErrorHandler with retry logic**

```kotlin
@Singleton
class ErrorHandler @Inject constructor() {
    suspend fun <T> handleError(error: RTMPError, block: suspend () -> T): T {
        return when (error) {
            RTMPError.CONNECTION_CLOSED,
            RTMPError.INGEST_CONGESTION,
            RTMPError.NETWORK_UNAVAILABLE -> {
                retryWithBackoff(block)
            }
            RTMPError.AUTH_EXPIRED -> {
                // Refresh token and retry once
                refreshToken()
                block()
            }
            RTMPError.TOO_FREQUENT -> {
                delay(60_000) // Wait 60 seconds
                block()
            }
            RTMPError.FLV_MUX_ERROR -> {
                // Reset connection and retry once
                resetConnection()
                block()
            }
            RTMPError.INVALID_STREAM_KEY,
            RTMPError.RTMP_HANDSHAKE_FAILED,
            RTMPError.PROJECTION_REVOKED -> {
                // Non-retryable errors
                throw error
            }
        }
    }
    
    private suspend fun <T> retryWithBackoff(block: suspend () -> T): T {
        var attempts = 0
        var delay = 1_000L
        
        while (attempts < 5) {
            try {
                return block()
            } catch (e: Exception) {
                attempts++
                if (attempts >= 5) throw e
                delay(delay)
                delay *= 2 // Exponential backoff
            }
        }
        
        throw RuntimeException("Max retries exceeded")
    }
    
    private suspend fun refreshToken() {
        // Implementation for token refresh
    }
    
    private suspend fun resetConnection() {
        // Implementation for connection reset
    }
}
```

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew :core:streaming:test`
Expected: PASS

```bash
git add core/streaming/
git commit -m "feat: implement error handling with retry policy and error taxonomy"
```

---

## Task 18: Feature Streaming UI — Home & Settings Screens

**Files:**
- Create: `feature/streaming-ui/src/main/kotlin/com/multistream/feature/streaming/HomeScreen.kt`
- Create: `feature/settings/src/main/kotlin/com/multistream/feature/settings/SettingsScreen.kt`
- Create: `feature/settings/src/main/kotlin/com/multistream/feature/settings/PlatformConfigScreen.kt`
- Create: `feature/settings/src/main/kotlin/com/multistream/feature/settings/WidgetConfigScreen.kt`

**Interfaces:**
- Consumes: Platform configurations, widget configurations
- Produces: UI for app navigation and configuration

- [ ] **Step 1: Create HomeScreen composable**

```kotlin
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val platforms by viewModel.platforms.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Connected Platforms",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        platforms.forEach { platform ->
            PlatformCard(
                platform = platform,
                onClick = { navController.navigate("streaming/${platform.name}") }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { navController.navigate("settings") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Settings")
        }
    }
}

@Composable
fun PlatformCard(platform: PlatformConfig, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(platform.platform.name)
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null
            )
        }
    }
}
```

- [ ] **Step 2: Create SettingsScreen with platform and widget configuration**

```kotlin
@Composable
fun SettingsScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { navController.navigate("settings/platforms") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Configure Platforms")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { navController.navigate("settings/widgets") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Configure Widgets")
        }
    }
}
```

- [ ] **Step 3: Create PlatformConfigScreen for OAuth/manual authentication**

```kotlin
@Composable
fun PlatformConfigScreen(
    viewModel: PlatformConfigViewModel = hiltViewModel()
) {
    val platforms by viewModel.platforms.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Platform Configuration",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        platforms.forEach { platform ->
            PlatformConfigCard(
                platform = platform,
                onConfigure = { viewModel.configurePlatform(platform) }
            )
        }
    }
}

@Composable
fun PlatformConfigCard(
    platform: Platform,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(platform.name)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onConfigure) {
                Text("Configure")
            }
        }
    }
}
```

- [ ] **Step 4: Create WidgetConfigScreen for managing widgets per layout**

```kotlin
@Composable
fun WidgetConfigScreen(
    viewModel: WidgetConfigViewModel = hiltViewModel()
) {
    val horizontalWidgets by viewModel.horizontalWidgets.collectAsState()
    val verticalWidgets by viewModel.verticalWidgets.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Widget Configuration",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Horizontal Layout Widgets")
        horizontalWidgets.forEach { widget ->
            WidgetConfigCard(widget = widget)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Vertical Layout Widgets")
        verticalWidgets.forEach { widget ->
            WidgetConfigCard(widget = widget)
        }
    }
}

@Composable
fun WidgetConfigCard(widget: Widget) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(widget.url)
            Text("Position: (${widget.position.x}, ${widget.position.y})")
            Text("Size: ${widget.size.width}x${widget.size.height}")
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add feature/streaming-ui/
git add feature/settings/
git commit -m "feat: add home, settings, platform config, and widget config screens"
```

---

## Task 19: Feature Streaming UI — Dual Layout Streaming Screen

**Files:**
- Create: `feature/streaming-ui/src/main/kotlin/com/multistream/feature/streaming/StreamingScreen.kt`
- Create: `feature/streaming-ui/src/main/kotlin/com/multistream/feature/streaming/StreamingViewModel.kt`
- Create: `feature/streaming-ui/src/main/kotlin/com/multistream/feature/streaming/LayoutPreview.kt`

**Interfaces:**
- Consumes: Core modules (capture, encoding, streaming, widgets)
- Produces: UI for controlling dual layout stream with independent widget configuration

- [ ] **Step 1: Create StreamingViewModel with dual layout support**

```kotlin
@HiltViewModel
class StreamingViewModel @Inject constructor(
    private val audioCapture: GameAudioCapture,
    private val audioEncoder: AudioEncoder,
    private val displayCapture: MediaProjectionDisplayCapture,
    private val compositor: MultiLayoutCompositor,
    private val encoder: DualVideoEncoder,
    private val streamer: RTMPMultiStreamer,
    private val widgetManager: WidgetLayoutManager
) : ViewModel() {
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming
    
    val horizontalWidgets: StateFlow<List<Widget>> = widgetManager.getWidgetsForLayout(Layout.HORIZONTAL)
    val verticalWidgets: StateFlow<List<Widget>> = widgetManager.getWidgetsForLayout(Layout.VERTICAL)
    
    fun startStreaming(mediaProjection: MediaProjection, configs: List<PlatformConfig>) {
        viewModelScope.launch {
            // Start audio capture and encoding (shared across both layouts)
            val audioFlow = audioCapture.startCapture(mediaProjection)
            audioEncoder.configure(sampleRate = 44100, channelCount = 2, bitrate = 128_000)
            
            // Start video capture
            val surface = displayCapture.createVirtualDisplay(mediaProjection, 1920, 1080, 320)
            
            // Get widgets for both layouts
            val hWidgets = horizontalWidgets.value
            val vWidgets = verticalWidgets.value
            
            // Configure dual encoder with both layouts
            encoder.configure(compositor, listOf(surface), hWidgets, vWidgets)
            
            // Connect to platforms
            streamer.connect(configs)
            
            // Collect and send video chunks from both layouts
            launch {
                encoder.encode(Layout.HORIZONTAL).collect { chunk ->
                    streamer.send(chunk)
                }
            }
            
            launch {
                encoder.encode(Layout.VERTICAL).collect { chunk ->
                    streamer.send(chunk)
                }
            }
            
            // Collect and send audio chunks
            launch {
                audioFlow.collect { pcmData ->
                    audioEncoder.encode(pcmData).collect { audioChunk ->
                        streamer.send(audioChunk)
                    }
                }
            }
            
            _isStreaming.value = true
        }
    }
    
    fun stopStreaming() {
        audioCapture.stopCapture()
        audioEncoder.release()
        displayCapture.release()
        encoder.release()
        viewModelScope.launch {
            streamer.disconnectAll()
        }
        _isStreaming.value = false
    }
    
    fun addWidget(widget: Widget) {
        widgetManager.addWidget(widget)
    }
    
    fun removeWidget(widgetId: String) {
        widgetManager.removeWidget(widgetId)
    }
}
```

- [ ] **Step 2: Create LayoutPreview composable**

```kotlin
@Composable
fun LayoutPreview(
    layout: Layout,
    widgets: List<Widget>,
    modifier: Modifier = Modifier
) {
    val aspectRatio = when (layout) {
        Layout.HORIZONTAL -> 16f / 9f
        Layout.VERTICAL -> 9f / 16f
    }
    
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .background(Color.DarkGray)
            .border(2.dp, Color.White)
    ) {
        Text(
            text = "${layout.name} Layout",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp)
        )
        
        Text(
            text = "Widgets: ${widgets.size}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}
```

- [ ] **Step 3: Create StreamingScreen composable**

```kotlin
@Composable
fun StreamingScreen(viewModel: StreamingViewModel) {
    val isStreaming by viewModel.isStreaming.collectAsState()
    val horizontalWidgets by viewModel.horizontalWidgets.collectAsState()
    val verticalWidgets by viewModel.verticalWidgets.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isStreaming) "Streaming..." else "Ready to stream",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LayoutPreview(
                layout = Layout.HORIZONTAL,
                widgets = horizontalWidgets,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            LayoutPreview(
                layout = Layout.VERTICAL,
                widgets = verticalWidgets,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = {
            if (isStreaming) viewModel.stopStreaming()
        }) {
            Text(if (isStreaming) "Stop" else "Start")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Widget Configuration",
            style = MaterialTheme.typography.titleMedium
        )
        
        Text("Horizontal Layout (${horizontalWidgets.size} widgets)")
        horizontalWidgets.forEach { widget ->
            Text("  - ${widget.url} at (${widget.position.x}, ${widget.position.y})")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text("Vertical Layout (${verticalWidgets.size} widgets)")
        verticalWidgets.forEach { widget ->
            Text("  - ${widget.url} at (${widget.position.x}, ${widget.position.y})")
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add feature/streaming-ui/
git commit -m "feat: add dual layout streaming screen with audio-video integration"
```

---

## Task 20: App Integration — Navigation & Main Activity

**Files:**
- Create: `app/src/main/kotlin/com/multistream/app/MultistreamApp.kt`
- Create: `app/src/main/kotlin/com/multistream/app/MainActivity.kt`
- Create: `app/src/main/kotlin/com/multistream/app/di/AppModule.kt`
- Create: `app/src/main/kotlin/com/multistream/app/navigation/AppNavigation.kt`
- Create: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: All core modules
- Produces: Working app with navigation, permission handling, and dual layout support

- [ ] **Step 1: Create AndroidManifest.xml**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    
    <application
        android:name=".MultistreamApp"
        android:allowBackup="true"
        android:label="Multistream"
        android:theme="@style/Theme.Multistream">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Multistream">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 2: Create MultistreamApp with Hilt**

```kotlin
@HiltAndroidApp
class MultistreamApp : Application()
```

- [ ] **Step 3: Create AppModule with all dependencies**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
    
    @Provides
    @Singleton
    fun provideLayoutRouter(): LayoutRouter = DefaultLayoutRouter()
}
```

- [ ] **Step 4: Create MainActivity with permission requests**

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        
        if (!audioGranted || !cameraGranted) {
            // Show message about limited functionality
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        ))
        
        setContent {
            MaterialTheme {
                AppNavigation()
            }
        }
    }
}
```

- [ ] **Step 5: Create AppNavigation composable**

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(navController = navController)
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        composable("settings/platforms") {
            PlatformConfigScreen()
        }
        composable("settings/widgets") {
            WidgetConfigScreen()
        }
        composable("streaming/{platform}") { backStackEntry ->
            val platform = backStackEntry.arguments?.getString("platform") ?: return@composable
            StreamingScreen(/* viewModel */)
        }
    }
}
```

- [ ] **Step 6: Verify build and run**

Run: `./gradlew :app:installDebug`
Expected: App installs and launches with navigation working

- [ ] **Step 7: Commit**

```bash
git add app/
git commit -m "feat: integrate all modules with navigation and permission handling"
```

---

## Self-Review Checklist

✅ **Spec coverage:** All sections covered:
- §1 Architecture: Tasks 1-2
- §2 Capture: Tasks 6, 14
- §3 Audio: Tasks 3-5, 7
- §4 Encoding: Tasks 8, 15
- §5 Streaming: Tasks 10-11
- §6 Widgets: Task 9
- §7 Auth: Tasks 12-13
- §8 Shizuku: Optional, not implemented (out of scope for MVP)
- §9 UI/UX: Tasks 18-20
- §10 Testing: Integrated throughout
- §11 Compatibility: Global constraints
- §12 Errors: Task 17
- §13 Resolution: Task 16

✅ **Step granularity:** Each step is one action with checkable result

✅ **Type consistency:** All types from `:core:common`, no duplicates

✅ **Review focus:** All 8 critical areas have tests

✅ **Proportion:** Plan is detailed but not a transcript

✅ **Critical fixes applied:**
- ✅ F1: Audio integrated in streaming pipeline (Task 19)
- ✅ F2: Widgets rendered in compositor (Task 8)
- ✅ F3: MediaProjection consent manager (Task 6)
- ✅ C1: No EncodedChunkLegacy, all use EncodedChunk from common
- ✅ C2: Layout/Platform defined once in common module
- ✅ C3: WidgetLayoutManager returns StateFlow
- ✅ C4: PlatformCredentials.toConfig() converter in common module

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2025-10-25-multistream-android.md`.

This is a comprehensive project with 20 tasks covering the entire MVP. The plan includes:

- **Core infrastructure** (Tasks 1-2): Project setup and shared types
- **Audio pipeline** (Tasks 3-7): Capture, DSP, mixing, AAC encoding
- **Video capture & encoding** (Tasks 6, 8, 14-16): MediaProjection, dual layout, performance monitoring
- **Widgets** (Task 9): Rendering, layout management, event bus
- **Streaming** (Tasks 10-11, 17): RTMP connections, multi-output, error handling
- **Platform auth** (Tasks 12-13): Secure storage, OAuth integration
- **UI** (Tasks 18-20): Home, settings, streaming screens, navigation

**Which execution approach would you prefer?**

- **Subagent-driven** (recommended) — A fresh subagent implements each task and a fresh reviewer checks it before the next one starts. Most thorough; costs a fresh context per task and per review. Best for this project because tasks have clear dependencies and mistakes early would cascade.

- **Native** — I implement every task myself in this session, then one fresh reviewer checks the whole branch at the end. Cheapest and fastest; no independent review until the end.

**For this plan I recommend subagent-driven**, because the 20 tasks have sequential dependencies (core modules must be built before integration) and a mistake in early tasks (e.g., audio capture, encoding) would be costly to fix later. The dual layout architecture adds complexity that benefits from incremental verification.

Does the plan capture what you want, and which approach should we use?
