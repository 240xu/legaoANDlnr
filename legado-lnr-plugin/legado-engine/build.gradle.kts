plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.legado.engine"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Rhino JS engine
    implementation("org.mozilla:rhino:1.7.15")
    // Jsoup HTML parser
    implementation("org.jsoup:jsoup:1.18.3")
    // JSON Path
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.14")
    // Gson (for legacy compatibility)
    implementation("com.google.code.gson:gson:2.11.0")
    // Kotlin serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Apache Commons Text (StringEscapeUtils)
    implementation("org.apache.commons:commons-text:1.12.0")
    // AndroidX annotation (for @Keep)
    compileOnly("androidx.annotation:annotation:1.9.1")
}