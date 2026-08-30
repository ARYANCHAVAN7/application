import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.application"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.application"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val localProperties = rootProject.file("local.properties")
        val props = Properties()
        if (localProperties.exists()) {
            props.load(localProperties.inputStream())
        }

        val mapboxAccessToken = props.getProperty("MAPBOX_ACCESS_TOKEN") ?: ""
        val googleWebClientId = props.getProperty("GOOGLE_WEB_CLIENT_ID") ?: ""

        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", "\"${mapboxAccessToken}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId}\"")
        manifestPlaceholders["MAPBOX_ACCESS_TOKEN"] = mapboxAccessToken

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.googleid)
    implementation(libs.material)
    // Google Play Services Location is required for FusedLocationProviderClient (GPS functionality)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Mapbox Maps SDK for map functionality
    implementation(libs.mapbox.maps)
    implementation("com.mapbox.extension:maps-style:11.29.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}