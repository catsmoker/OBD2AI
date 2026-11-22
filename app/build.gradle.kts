import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

fun localSettings(name: String) = Properties().apply {
    rootProject.file(name).takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val signing = localSettings("signing.properties")
val ads = localSettings("local.properties")
val localSigner = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !signing.getProperty(it).isNullOrBlank() }
val injected = listOf("store.file", "store.password", "key.alias", "key.password")
    .associateWith { providers.gradleProperty("android.injected.signing.$it").orNull }
val releaseAlias = if (injected.values.any { it != null }) {
    injected["key.alias"].takeIf { injected.values.all { value -> !value.isNullOrBlank() } }
} else signing.getProperty("keyAlias").takeIf { localSigner }
val releaseAds = !releaseAlias.isNullOrBlank() && !releaseAlias.equals("androiddebugkey", true)
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
fun releaseId(key: String, fallback: String) =
    ads.getProperty(key)?.trim()?.takeIf { releaseAds && it.isNotEmpty() } ?: fallback

android {
    namespace = "com.catsmoker.obd2ai"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.catsmoker.obd2ai"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        signingConfig = signingConfigs.getByName("debug")
        manifestPlaceholders["admobAppId"] = testAppId
        buildConfigField("String", "BANNER_ID", "\"$testBannerId\"")
    }

    if (localSigner) signingConfigs.create("publisher") {
        storeFile = rootProject.file(signing.getProperty("storeFile"))
        storePassword = signing.getProperty("storePassword")
        keyAlias = signing.getProperty("keyAlias")
        keyPassword = signing.getProperty("keyPassword")
    }

    buildTypes {
        release {

            // Enables code-related app optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName(if (localSigner) "publisher" else "debug")
            manifestPlaceholders["admobAppId"] = releaseId("ADMOB_APP_ID", testAppId)
            buildConfigField("String", "BANNER_ID", "\"${releaseId("ADMOB_BANNER_ID", testBannerId)}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    testOptions {
        // android.util.Log is not mocked on the JVM; make it a no-op so pure
        // logic can be unit tested without Robolectric.
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        // Full Compose UI: no XML layouts, no view binding.
        compose = true
        buildConfig = true
        viewBinding = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildToolsVersion = "36.1.0"
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.speedviewlib)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(project(":core:obd"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:ai"))
    implementation(project(":core:prefs"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Jetpack Compose (BOM pins the versions)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlin.obd.api)
    // Liquid Glass (Backdrop) — effect primitives only, no components.
    implementation(libs.kyant.backdrop)
    implementation(libs.play.services.ads)
    implementation(libs.play.billing.ktx)
    implementation(libs.ump)
    implementation(libs.firebase.analytics)
    implementation(libs.androidx.core.splashscreen)
    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (the android.jar stubs throw "not mocked")
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}