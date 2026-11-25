// Top-level plugins shared by the application modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false
}
