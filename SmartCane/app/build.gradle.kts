import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

fun localOrGradleProperty(name: String): String =
    localProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: ""

val kakaoRestApiKey = localOrGradleProperty("KAKAO_REST_API_KEY")
val kakaoNativeAppKey = localOrGradleProperty("KAKAO_NATIVE_APP_KEY")

android {
    namespace = "com.ssafy.smartcane"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ssafy.smartcane"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoRestApiKey\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
    androidResources {
        noCompress.add("tflite")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

tasks.register<Copy>("copyApk") {
    val variantName = if (gradle.startParameter.taskNames.any { it.contains("Release") }) "release" else "debug"
    from(layout.buildDirectory.dir("outputs/apk/$variantName"))
    into(layout.buildDirectory.dir("outputs/named-apk"))
    include("app-*.apk")
    rename("app-(.*)\\.apk", "SmartCane.apk")
}

afterEvaluate {
    tasks.named("assembleDebug").configure { finalizedBy("copyApk") }
    tasks.named("assembleRelease").configure { finalizedBy("copyApk") }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // OkHttp WebSocket
    implementation(libs.okhttp)
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kakao.map)
    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // LiteRT includes the org.tensorflow.lite Interpreter API used by the legacy YOLO path.
    implementation("com.google.ai.edge.litert:litert:2.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.github.jiangdongguo.AndroidUSBCamera:libausbc:3.2.7") {
        isTransitive = false
    }
    implementation("com.github.jiangdongguo.AndroidUSBCamera:libnative:3.2.7") {
        isTransitive = false
    }
    implementation(files("libs/libuvc-3.2.7-android14.aar"))
    implementation("com.elvishew:xlog:1.11.0")
    implementation("com.google.android.material:material:1.12.0")
    // ARCore (depth + semantics) — Lumen2 안전보행 모듈에서 사용
    implementation(libs.arcore)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Wearable Data Layer API (워치 ↔ 폰 메시지 전송)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
}
