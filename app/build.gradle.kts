import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Версия приложения: GitHub Actions передаёт её из тега (v1.2.3 → -PappVersion=1.2.3).
// По ней приложение сравнивает себя с последним релизом на GitHub.
val appVersion = (findProperty("appVersion") as String?) ?: "1.0.0"
val appVersionCode = appVersion.split('.').map { it.toIntOrNull() ?: 0 }
    .let { it.getOrElse(0) { 0 } * 10000 + it.getOrElse(1) { 0 } * 100 + it.getOrElse(2) { 0 } }

// Ключ подписи: из переменных окружения (GitHub Actions) или keystore.properties (локально, не в git).
// Один ключ для Android Studio и релизов — иначе обновление с GitHub не встанет поверх установленной версии.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(property: String, env: String): String? = System.getenv(env) ?: keystoreProperties.getProperty(property)

// Telegram-бот для отчётов диагностики: telegram.properties (локально, не в git) или секреты GitHub. См. docs/DEBUG_REPORT.md
val telegramProperties = Properties().apply {
    rootProject.file("telegram.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun telegramValue(property: String, env: String): String =
    (System.getenv(env)?.takeIf { it.isNotBlank() } ?: telegramProperties.getProperty(property) ?: "").trim()
        .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.carscan"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.carscan"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TG_BOT_TOKEN", "\"${telegramValue("botToken", "CARSCAN_TG_BOT_TOKEN")}\"")
        buildConfigField("String", "TG_CHAT_ID", "\"${telegramValue("chatId", "CARSCAN_TG_CHAT_ID")}\"")
    }

    val releaseKeyFile = signingValue("storeFile", "CARSCAN_KEYSTORE")
    signingConfigs {
        if (releaseKeyFile != null) {
            create("carscan") {
                storeFile = rootProject.file(releaseKeyFile)
                storePassword = signingValue("storePassword", "CARSCAN_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "CARSCAN_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "CARSCAN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (releaseKeyFile != null) signingConfig = signingConfigs.getByName("carscan")
        }
        debug {
            if (releaseKeyFile != null) signingConfig = signingConfigs.getByName("carscan")
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.webkit:webkit:1.9.0")
    testImplementation(libs.junit)
    // Настоящая реализация org.json для локальных unit-тестов (в android.jar только заглушки)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // SceneView для 3D (используем версию, совместимую с Compose)
    implementation("io.github.sceneview:sceneview:2.3.0") // Или sceneview-compose, когда он выйдет из беты
    // Koin для внедрения зависимостей (DI)
    implementation("io.insert-koin:koin-androidx-compose:3.5.3")
    implementation("io.insert-koin:koin-android:3.5.3")
}
