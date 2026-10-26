package com.multistream.core.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaProjectionConsentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    
    private val _mediaProjection = MutableStateFlow<MediaProjection?>(null)
    val mediaProjection: StateFlow<MediaProjection?> = _mediaProjection.asStateFlow()
    
    private val _isConsentGranted = MutableStateFlow(false)
    val isConsentGranted: StateFlow<Boolean> = _isConsentGranted.asStateFlow()
    
    fun createConsentIntent(): Intent {
        return mediaProjectionManager.createScreenCaptureIntent()
    }
    
    fun handleConsentResult(resultCode: Int, data: Intent?): Boolean {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val projection = mediaProjectionManager.getMediaProjection(resultCode, data)
            _mediaProjection.value = projection
            _isConsentGranted.value = true
            return true
        }
        return false
    }
    
    fun getMediaProjection(): MediaProjection? {
        return _mediaProjection.value
    }
    
    fun revokeConsent() {
        _mediaProjection.value?.stop()
        _mediaProjection.value = null
        _isConsentGranted.value = false
    }
    
    fun isConsentValid(): Boolean {
        return _mediaProjection.value != null && _isConsentGranted.value
    }
}
