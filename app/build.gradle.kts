import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Semantic version (MAJOR.MINOR.PATCH). Bump this for each release and tag the commit vX.Y.Z.
val appVersion = "0.5.0"

android {
    namespace = "net.meshkati.umm"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.meshkati.umm"
        minSdk = 26
        targetSdk = 36
        versionName = appVersion
        // MAJOR * 10000 + MINOR * 100 + PATCH, so MINOR and PATCH must each stay below 100.
        versionCode = appVersion.split(".").map(String::toInt).let { (major, minor, patch) ->
            require(minor < 100 && patch < 100) { "MINOR and PATCH must be below 100: $appVersion" }
            major * 10000 + minor * 100 + patch
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
