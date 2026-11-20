plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val persistentKeystoreFile = System.getenv("APP_OBSERVER_KEYSTORE_FILE")
val persistentKeystorePassword = System.getenv("APP_OBSERVER_KEYSTORE_PASSWORD")
val persistentKeyAlias = System.getenv("APP_OBSERVER_KEY_ALIAS")
val persistentKeyPassword = System.getenv("APP_OBSERVER_KEY_PASSWORD")

android {
    namespace = "com.ysfclc.appobserver"
    compileSdk = 35
    defaultConfig {
        // This is the permanent production package. Do not change it after the first
        // persistent release is installed; future APKs must use the same signing key.
        applicationId = "com.ysfclc.appobserver.capture"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.4.0-visual-rules"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("persistentRelease") {
            if (!persistentKeystoreFile.isNullOrBlank()) {
                storeFile = file(persistentKeystoreFile)
            }
            storePassword = persistentKeystorePassword
            keyAlias = persistentKeyAlias
            keyPassword = persistentKeyPassword
        }
    }
    buildTypes {
        debug {
            // Debug APKs remain a separate throwaway identity. They are not release updates.
            applicationIdSuffix = ".generic.viewer.diagnostics.debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("persistentRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
