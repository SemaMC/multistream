plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kapt)
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
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
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle)
}
