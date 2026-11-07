import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Firebase 설정 — libri/local.properties 에서 읽는다 (git 에 올리지 않음). 비어 있으면 '혼자 테스트(봇)' 모드만 동작
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String): String = localProps.getProperty(key, "")

android {
    namespace = "kr.kw.libri"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "kr.kw.libri"
        minSdk = 23          // TEMI V1·V2(Android 6.0.1 / API 23)까지 설치 가능하도록
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FIREBASE_DB_URL", "\"${localProp("firebase.dbUrl")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${localProp("firebase.apiKey")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${localProp("firebase.appId")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${localProp("firebase.projectId")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.face.detection)
    implementation(libs.temi.sdk)

    // 네컷 사진 촬영, QR 생성 및 Supabase 연동
    // 1. CameraX (사진 촬영)
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // 2. QR 코드 생성 (ZXing)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // 3. Supabase 연동 (Storage 및 Database)
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.1"))
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:2.3.12")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
