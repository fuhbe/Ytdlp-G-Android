plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.fuhbe.ytdlpg"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fuhbe.ytdlpg"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0-beta.1"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { jniLibs.useLegacyPackaging = true }
    sourceSets["main"].res.srcDir(layout.buildDirectory.dir("generated/runtime/res"))
}

val prepareRuntime by tasks.registering(Exec::class) {
    val output = layout.buildDirectory.dir("generated/runtime/res")
    inputs.file(rootProject.file("scripts/prepare_runtime.py"))
    outputs.dir(output)
    commandLine("python3", rootProject.file("scripts/prepare_runtime.py"), output.get().asFile)
}
tasks.named("preBuild").configure { dependsOn(prepareRuntime) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    testImplementation("junit:junit:4.13.2")
}
