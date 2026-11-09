plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "cl.inacap.estacionamiento"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.inacap.estacionamiento"
        // Android 8.0 o superior: necesario para PBKDF2WithHmacSHA256 y java.util.Base64
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Las vistas son layouts XML (res/layout) con Activities del framework: no se necesita Compose.
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // org.json real para las pruebas unitarias (en el JVM de pruebas Android solo trae stubs)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
