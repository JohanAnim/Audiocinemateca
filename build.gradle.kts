// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) version "2.2.0" apply false
    alias(libs.plugins.ksp) version "2.2.0-2.0.2" apply false
    alias(libs.plugins.hilt.android) apply false
    
}