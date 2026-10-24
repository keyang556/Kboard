plugins {
    alias(libs.plugins.android.application) apply false
    // AGP compiles Kotlin with whichever Kotlin Gradle plugin is on the build classpath.
    // Declaring it here keeps the compiler in step with the Kotlin libraries we use.
    alias(libs.plugins.kotlin.android) apply false
}
