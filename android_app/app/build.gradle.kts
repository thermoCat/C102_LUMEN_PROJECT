import java.util.Properties

plugins {
    id("com.android.application")
}

val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        releaseKeystorePropertiesFile.inputStream().use(::load)
    }
}
val hasReleaseKeystore = releaseKeystorePropertiesFile.exists()

android {
    namespace = "com.ssafy.trafficlightstandalone.integrated"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ssafy.trafficlightstandalone.integrated.v4blinkstate"
        minSdk = 23
        targetSdk = 36
        versionCode = 2026042304
        versionName = "4.20260423.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.whenTaskAdded {
    if (name == "packageDebug") {
        doLast {
            val outputDir = project.layout.buildDirectory.dir("outputs/apk/debug").get().asFile
            outputDir.listFiles { _, n -> n.endsWith(".apk") && n != "trafficlight_v4_blink_state_20260423-debug.apk" }
                ?.forEach { apk ->
                    val target = File(apk.parent, "trafficlight_v4_blink_state_20260423-debug.apk")
                    if (target.exists()) target.delete()
                    apk.renameTo(target)
                }
        }
    }
    if (name == "packageRelease") {
        doLast {
            val outputDir = project.layout.buildDirectory.dir("outputs/apk/release").get().asFile
            outputDir.listFiles { _, n -> n.endsWith(".apk") && n != "trafficlight_v4_blink_state_20260423-release.apk" }
                ?.forEach { apk ->
                    val target = File(apk.parent, "trafficlight_v4_blink_state_20260423-release.apk")
                    if (target.exists()) target.delete()
                    apk.renameTo(target)
                }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    implementation("androidx.camera:camera-core:1.6.0")
    implementation("androidx.camera:camera-camera2:1.6.0")
    implementation("androidx.camera:camera-lifecycle:1.6.0")
    implementation("androidx.camera:camera-view:1.6.0")

    implementation("com.google.ai.edge.litert:litert:2.1.0")

    testImplementation("junit:junit:4.13.2")
}
