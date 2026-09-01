plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.faccioio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.faccioio.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "0.3.20"
    }

    signingConfigs {
        create("stableRelease") {
            val keystorePath = System.getenv("FACCIO_IO_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("FACCIO_IO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FACCIO_IO_KEY_ALIAS")
                keyPassword = System.getenv("FACCIO_IO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("stableRelease")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    

    compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlinOptions {
    jvmTarget = "17"
}
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
