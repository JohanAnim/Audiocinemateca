// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) version "8.10.1" apply false
    alias(libs.plugins.kotlin.android) version "2.2.0" apply false
    alias(libs.plugins.ksp) version "2.2.0-2.0.2" apply false
    alias(libs.plugins.hilt.android) apply false
    
}

buildscript {
    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.57") // Asegúrate de que la versión coincida con la de libs.versions.toml
    }
}