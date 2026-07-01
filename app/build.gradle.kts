plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Heavy on-device engines (llama.cpp MT + sherpa-onnx voice) are opt-in. The
// default build and CI assemble and test the app on the stub pipeline with no
// NDK, so they stay fast and green. Produce a real device build with:
//   ./gradlew :app:assembleDebug -PwithNative=true -PllamaCppDir=/path/to/llama.cpp
val withNative = (project.findProperty("withNative") as String?)?.toBoolean() ?: false
val llamaCppDir = project.findProperty("llamaCppDir") as String?

android {
    namespace = "ai.whatyousay"
    compileSdk = 35

    if (withNative) {
        ndkVersion = "26.3.11579264"
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    defaultConfig {
        applicationId = "ai.whatyousay"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Native ABIs for llama.cpp / sherpa-onnx. arm64 first; phones are arm64.
        // Override with -PnativeAbis=arm64-v8a to build a single ABI.
        ndk {
            val abis = (project.findProperty("nativeAbis") as String?)
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("arm64-v8a", "x86_64")
            abiFilters += abis
        }

        if (withNative) {
            externalNativeBuild {
                cmake {
                    cppFlags += "-O3"
                    arguments += "-DANDROID_STL=c++_shared"
                    llamaCppDir?.let { arguments += "-DLLAMA_CPP_DIR=$it" }
                }
            }
        }
    }

    // The sherpa-onnx voice wrappers depend on a vendored AAR that is not part of
    // the default build, so they live in their own source set, compiled only when
    // native engines are enabled.
    if (withNative) {
        sourceSets.getByName("main").java.srcDir("src/sherpa/kotlin")
    }

    // Release signing reads from Gradle properties or env so the keystore never
    // lives in the repo. Provide -PreleaseStoreFile=... (and the passwords) or
    // the WYS_STORE_FILE / WYS_STORE_PASSWORD / WYS_KEY_ALIAS / WYS_KEY_PASSWORD
    // env vars to produce a signed release APK. Absent those, release stays
    // unsigned so CI and contributors can still assemble it.
    val releaseStoreFile = (project.findProperty("releaseStoreFile") as String?)
        ?: System.getenv("WYS_STORE_FILE")
    if (releaseStoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = (project.findProperty("releaseStorePassword") as String?)
                    ?: System.getenv("WYS_STORE_PASSWORD")
                keyAlias = (project.findProperty("releaseKeyAlias") as String?)
                    ?: System.getenv("WYS_KEY_ALIAS")
                keyPassword = (project.findProperty("releaseKeyPassword") as String?)
                    ?: System.getenv("WYS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Minification is off: the app binds llama.cpp/sherpa-onnx over JNI by
            // fully-qualified name, so renaming those classes would break the
            // native bridges. Keep the release build faithful to what we test.
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    // Native voice engine. llama.cpp (MT) is built from source via CMake into
    // libwhatyousay_llama.so; sherpa-onnx (STT + VAD + TTS) ships as a prebuilt
    // AAR vendored into app/libs (built from the pinned sherpa-onnx release, see
    // src/sherpa/README.md). Both are linked only when -PwithNative=true.
    if (withNative) {
        val sherpaAar = file("libs/sherpa-onnx.aar")
        if (sherpaAar.exists()) {
            implementation(files(sherpaAar))
        }
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
