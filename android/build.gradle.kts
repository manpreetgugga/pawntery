import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// libGDX ships its native code (.so files) inside jars; copyAndroidNatives unpacks them into libs/<abi>/.
val natives: Configuration by configurations.creating
val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

android {
    namespace = "com.pawtillery.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pawtillery.game"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // A fixed debug key (Android's standard debug passwords), so each test APK built on GitHub
        // installs over the last one. Play Store builds use your own upload key, never this one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            res.srcDir("res")
            assets.srcDir("../assets")
            jniLibs.srcDir("libs")
        }
    }

    buildTypes {
        release {
            // Shrinking stays off until the libGDX/Box2D keep rules are tested on a device.
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val gdxVersion = libs.versions.gdx.get()

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.android)
    abis.forEach { abi ->
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

val copyAndroidNatives by tasks.registering(Copy::class) {
    abis.forEach { abi ->
        from(provider { natives.files.filter { it.name.endsWith("natives-$abi.jar") }.map { zipTree(it) } }) {
            include("*.so")
            into(abi)
        }
    }
    into(layout.projectDirectory.dir("libs"))
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
