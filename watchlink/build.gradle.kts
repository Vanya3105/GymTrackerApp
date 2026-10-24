// What the phone app and the watch app say to each other over the Wearable Data Layer.
// Shared so the two sides can't drift apart.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.gymtracker.watchlink"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
    // android.jar's org.json is a stub in local unit tests.
    testImplementation(libs.json)
}
