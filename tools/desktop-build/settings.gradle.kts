// Builds and tests the game (core + desktop test launcher) without the Android SDK or the
// Android Gradle plugin, e.g. on a CI machine: ./gradlew -p tools/desktop-build :core:test
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "pawtillery-desktop"
include(":core", ":lwjgl3")
project(":core").projectDir = file("../../core")
project(":lwjgl3").projectDir = file("../../lwjgl3")
