plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun injected(name: String, fallback: String = ""): String =
    providers.gradleProperty(name).orNull ?: System.getenv(name) ?: fallback

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.wulisu.licenseoverlay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wulisu.licenseoverlay"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"

        buildConfigField("String", "DEFAULT_BASE_URL", buildConfigString(injected("ACTIVATION_BASE_URL", "http://124.223.176.99")))
        buildConfigField("String", "DEFAULT_BASIC_USERNAME", buildConfigString(injected("ACTIVATION_BASIC_USER")))
        buildConfigField("String", "DEFAULT_BASIC_PASSWORD", buildConfigString(injected("ACTIVATION_BASIC_PASSWORD")))
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
