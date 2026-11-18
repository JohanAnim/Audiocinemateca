plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    // Parcelize ahora se aplica así, AGP 9.0 se encarga de enlazarlo con su Kotlin interno
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.google.services)
}

base {
    archivesName.set("Audiocinemateca")
}

android {
    namespace = "com.johang.audiocinemateca"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.johang.audiocinemateca"
        minSdk = 25
        targetSdk = 37
        versionCode = 8
        versionName = "3.4.0-beta.2"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/*.DSA"
            excludes += "META-INF/*.SF"
            excludes += "META-INF/*.RSA"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE-LGPL-3.txt"
            excludes += "META-INF/LICENSE-LGPL-2.1.txt"
            excludes += "META-INF/LICENSE-W3C-TEST"
        }
    }
}

composeCompiler {
    includeSourceInformation = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.work.runtime.ktx)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Gson
    implementation(libs.gson)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // ExoPlayer & Cast
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.common)
    implementation(libs.exoplayer.session)
    implementation(libs.exoplayer.okhttp)
    implementation(libs.exoplayer.cast)
    implementation(libs.play.services.cast.framework)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Google Auth & Credentials
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Jetpack Compose & Navigation Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
