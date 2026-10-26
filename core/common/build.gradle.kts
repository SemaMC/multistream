plugins {
    alias(libs.plugins.android.library)
    }

android {
    namespace = "com.multistream.core.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation(libs.kotlin.stdlib)
    
    testImplementation("junit:junit:4.13.2")
}






