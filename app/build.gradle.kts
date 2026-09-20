plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.catsmoker.obd2ai"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.catsmoker.obd2ai"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        signingConfig = signingConfigs.getByName("debug")
    }

    buildTypes {
        release {

            // Enables code-related app optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
    implementation(libs.play.services.ads)
    implementation(libs.firebase.analytics)
    implementation(libs.androidx.core.splashscreen)
    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (the android.jar stubs throw "not mocked")
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}