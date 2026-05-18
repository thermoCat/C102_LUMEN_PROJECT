plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ssafy.smartcane"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ssafy.smartcane"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
    }

}

tasks.register<Copy>("copyApk") {
    val variantName = if (gradle.startParameter.taskNames.any { it.contains("Release") }) "release" else "debug"
    from(layout.buildDirectory.dir("outputs/apk/$variantName"))
    into(layout.buildDirectory.dir("outputs/named-apk"))
    include("wear-*.apk")
    rename("wear-(.*)\\.apk", "SmartCane_wear.apk")
}

afterEvaluate {
    tasks.named("assembleDebug").configure { finalizedBy("copyApk") }
    tasks.named("assembleRelease").configure { finalizedBy("copyApk") }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.androidx.activity.compose)
}
