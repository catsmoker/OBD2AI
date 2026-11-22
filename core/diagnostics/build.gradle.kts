plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.catsmoker.obd2ai.diagnostics"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 24
        testOptions {
            unitTests.isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.org.json)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    // The dictionary-coverage test asserts the bundled DTC table covers the
    // demo source's codes; test-only edge, so no production cycle exists.
    testImplementation(project(":core:obd"))
}
