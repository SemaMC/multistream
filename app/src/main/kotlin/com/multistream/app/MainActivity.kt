package com.multistream.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import com.multistream.app.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint

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

        requestPermissionLauncher.launch(
            buildList {
                add(Manifest.permission.RECORD_AUDIO)
                add(Manifest.permission.CAMERA)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+: needed to show the foreground service notification.
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        )

        setContent {
            MaterialTheme {
                AppNavigation()
            }
        }
    }
}
