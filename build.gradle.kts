// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    val room_version = "2.7.0"
    id("androidx.room") version "$room_version" apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

}