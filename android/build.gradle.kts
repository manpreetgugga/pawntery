import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// libGDX ships its native code (.so files) inside jars; copyAndroidNatives unpacks them into libs/<abi>/.
val natives: Configuration by configurations.creating
val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

// GitHub numbers its builds. Test builds take that number, so each one is newer than the last and
// installed copies know when to update. Builds made on your own computer are build 0.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0

android {
    namespace = "com.pawtillery.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pawtillery.game"
        minSdk = 26
        targetSdk = 36
        versionCode = 1 + buildNumber
        versionName = if (buildNumber > 0) "1.0.0 (test build $buildNumber)" else "1.0.0"
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

// The version of this build, for the update.json that installed test builds read to find newer ones.
val writeUpdateInfo by tasks.registering {
    val code = android.defaultConfig.versionCode
    val name = android.defaultConfig.versionName
    val out = layout.buildDirectory.file("outputs/update-info.json")
    outputs.file(out)
    doLast { out.get().asFile.writeText("{\"versionCode\": $code, \"versionName\": \"$name\"}\n") }
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
