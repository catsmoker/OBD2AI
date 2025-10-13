import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.catsmoker.obd2ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.catsmoker.obd2ai"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        signingConfig = signingConfigs.getByName("debug")
    }

    // Load API key from local.properties
    val localProperties = Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            load(localPropertiesFile.inputStream())
        }
    }
    val apiKey = localProperties.getProperty("OPENAI_API_KEY", "")

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "OPENAI_API_KEY", "\"$apiKey\"")
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            buildConfigField("String", "OPENAI_API_KEY", "\"$apiKey\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // IMPORTANT: Compose is disabled as we are using Views and Fragments
        compose = false
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
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.speedviewlib)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.kotlin.obd.api)
    implementation(libs.openai.client)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.slf4j.nop)
    implementation(libs.play.services.ads)
    implementation(libs.firebase.analytics)
    implementation(libs.androidx.core.splashscreen)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}