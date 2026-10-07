plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "uk.scimone.diafit"
    compileSdk = 37

    defaultConfig {
        applicationId = "uk.scimone.diafit"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_KEY", "\"\"")
        buildConfigField("String", "BASE_URL", "\"https://gluco.mooo.com\"")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.room.common.jvm)
    implementation(libs.androidx.room.runtime.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.health.connect.client)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20250517") // org.json is only a stub in Android unit tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Koin core features
    implementation(libs.koin.core)

    // Koin Android
    implementation(libs.koin.android)

    // Koin Compose
    implementation(libs.koin.androidx.compose)

    implementation(libs.coil.compose)

    implementation(libs.androidx.room.runtime)
    ksp("androidx.room:room-compiler:2.8.5")
    // if using Kotlin coroutines or RxJava with Room
    implementation(libs.androidx.room.ktx)

    implementation(libs.bundles.ktor)

    // Vico Charts (compose + M3 theming; `core` is folded into `compose` since 3.0)
    implementation("com.patrykandpatrick.vico:compose-m3:3.3.1")

    dependencies {
        implementation(libs.androidx.preference.ktx)
    }


}