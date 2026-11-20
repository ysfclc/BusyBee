plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.ysfclc.appobserver"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ysfclc.appobserver.capture"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.0-generic-privacy"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".generic.debug"
            versionNameSuffix = "-debug"
        }
        release { isMinifyEnabled = false }
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
