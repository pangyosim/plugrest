// ponytail: 단일 모듈. 루트가 곧 앱 모듈이라 APK는 build/outputs/apk/ 에 나옴.
plugins {
    id("com.android.application") version "8.8.2"
    id("org.jetbrains.kotlin.android") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

android {
    namespace = "ai.pleos.plugrest"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.pleos.plugrest"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // 데모 모드: "계산 중" 3분→20초, 충전 판정(SOC 상승 창) 3분→20초. ./gradlew -Pdemo=false 로 끔.
        buildConfigField("boolean", "DEMO", (project.findProperty("demo") ?: "true").toString())
        // Fake 차량 강제: ./gradlew -Pfake=true (Pleos 서비스가 없으면 자동으로 Fake)
        buildConfigField("boolean", "FORCE_FAKE", (project.findProperty("fake") ?: "false").toString())
    }
    buildTypes {
        release {
            // ponytail: 공모전 제출용. 디버그 키로 서명해 바로 설치 가능하게 함. 스토어 배포 시 교체.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    useLibrary("android.car") // CarUxRestrictionsManager (AAOS 시스템 이미지에만 존재, 런타임에 try/catch)
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:vehicle-data:1.1.11") // ErrorCode enum (POM에서 runtime scope라 명시 필요)
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.0") // SDK가 끌어오는 1.9.25 reflect와 stdlib 2.0 버전 정렬
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
}
