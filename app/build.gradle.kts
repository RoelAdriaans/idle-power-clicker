plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.idlepowerhelper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.idlepowerhelper"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

// After `./gradlew installDebug`, run `./gradlew enableA11y` to re-enable
// the Accessibility Service without opening phone settings.
tasks.register<Exec>("enableA11y") {
    val pkg = "com.example.idlepowerhelper"
    val svc = "$pkg/.SwipeAccessibilityService"
    commandLine(
        "adb", "shell", "settings", "put", "secure",
        "enabled_accessibility_services", svc
    )
    doLast {
        exec { commandLine("adb", "shell", "settings", "put", "secure", "accessibility_enabled", "1") }
    }
    description = "Re-enables the IPH Accessibility Service via ADB (dev only)"
}
