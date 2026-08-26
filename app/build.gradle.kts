plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kleinapps.kaspitracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kleinapps.kaspitracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            // pdfbox-android pulls in fontbox/etc. that duplicate these META-INF files.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Weekly unattended backup into Downloads.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Room (KSP, not kapt)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Offline PDF text extraction for imported Kaspi payment receipts (no network calls).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Offline OCR fallback: Kaspi transfer receipts are image-only PDFs with no
    // text layer, so PDFBox extracts nothing — the page is rendered to a bitmap
    // and recognized with Tesseract (rus+eng traineddata bundled in assets).
    implementation("com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Plain JVM unit tests for the parsers, the categorizer and the repository
    // rules (run with: gradle testDebugUnitTest).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // Real org.json for backup tests — the stubbed android.jar used by unit
    // tests returns default values for every method instead of parsing.
    testImplementation("org.json:json:20240303")
}
