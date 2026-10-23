plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
android {
    namespace = "com.fuhbe.ytdlpg.simple"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fuhbe.ytdlpg.minimal"
        minSdk = 26
        targetSdk = 35
        versionCode = 65
        versionName = "0.6.5-test"
        ndk { abiFilters += (project.findProperty("abis")?.toString() ?: "arm64-v8a").split(",") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "runtime"
    productFlavors { create("github") { dimension = "runtime" } }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { jniLibs.useLegacyPackaging = true; resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets["main"].java.srcDir("build/generated/upstream/java")
    sourceSets["main"].res.srcDir("build/generated/runtime/res")
    sourceSets["main"].assets.srcDir("build/generated/upstream/assets")
    testOptions.unitTests.isIncludeAndroidResources = true
    testBuildType = "release"
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
val prepareRuntime by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine("python3", "prepare.py")
    inputs.file(rootProject.file("upstream.json"))
    inputs.file(rootProject.file("prepare.py"))
    inputs.file(rootProject.file("runtime_patch.py"))
    inputs.dir(rootProject.file("runtime"))
    inputs.file(rootProject.file("../scripts/prepare_runtime.py"))
    inputs.dir(rootProject.file("plugins"))
    outputs.dir(layout.buildDirectory.dir("generated/upstream"))
    outputs.dir(layout.buildDirectory.dir("generated/runtime"))
}
tasks.named("preBuild").configure { dependsOn(prepareRuntime) }
dependencies {
    implementation("io.github.kyant0:backdrop:1.0.3")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui:1.10.1")
    implementation("androidx.compose.foundation:foundation:1.10.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    // Align the app and release instrumentation classpaths: Test Core uses the
    // suspend adapter absent from WorkManager's older transitive 1.1.0 version.
    implementation("androidx.concurrent:concurrent-futures-ktx:1.3.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("commons-io:commons-io:2.5")
    implementation("org.apache.commons:commons-compress:1.12")
    implementation("io.github.deniscerri:ytdlnis_packages.python:3.14.6")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.concurrent:concurrent-futures-ktx:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.10.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.1")
}
val quickJsNative by configurations.creating
dependencies { quickJsNative("io.github.junkfood02.youtubedl-android:library:0.18.1@aar") { isTransitive = false } }
val quickJsOutput = layout.buildDirectory.dir("generated/quickjs-native")
val prepareQuickJs by tasks.registering(Sync::class) {
    from({ zipTree(quickJsNative.singleFile) }) {
        include("jni/**/libqjs.so")
        eachFile { path = path.removePrefix("jni/") }
        includeEmptyDirs = false
    }
    into(quickJsOutput)
}
android.sourceSets["main"].jniLibs.srcDir(quickJsOutput)
tasks.named("preBuild").configure { dependsOn(prepareQuickJs) }
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    systemProperty("ytdlpg.root", rootProject.projectDir.absolutePath)
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach {
        System.getProperty(it)?.let { value -> systemProperty(it, value) }
    }
}
