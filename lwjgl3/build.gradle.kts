import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Desktop launcher for quick testing while developing. The game ships on Android only.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val gdxVersion = libs.versions.gdx.get()

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.lwjgl3)
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}

application {
    mainClass.set("com.pawtillery.game.lwjgl3.DesktopLauncherKt")
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
    }
}

tasks.named<JavaExec>("run") {
    workingDir = file("../assets")
}

tasks.register<JavaExec>("storeAssets") {
    group = "distribution"
    description = "Renders the Play Store icon, launcher icon layers and feature graphic into store/ and android/res/"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.pawtillery.game.lwjgl3.StoreAssetsKt")
    workingDir = file("../assets")
    args(file("..").absolutePath)
    jvmArgs(application.applicationDefaultJvmArgs)
}

tasks.register<JavaExec>("tour") {
    group = "verification"
    description = "Plays a scripted session and saves screenshots: --args=\"<out-dir> <width> <height>\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.pawtillery.game.lwjgl3.ScreenshotTourKt")
    workingDir = file("../assets")
    jvmArgs(application.applicationDefaultJvmArgs)
}
