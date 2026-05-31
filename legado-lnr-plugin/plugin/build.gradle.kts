import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.legado.lnr.plugin"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.legado.lnr.plugin"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach {
            val outputImpl = it as com.android.build.api.variant.impl.VariantOutputImpl
            val original = outputImpl.outputFileName.get()
            outputImpl.outputFileName = original.replace(".apk", ".apk.lnrp")
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("androidx.compose.runtime:runtime:1.10.3")
    implementation(platform("androidx.compose:compose-bom:2026.02.00"))
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    // Engine library
    implementation(project(":legado-engine"))
    // LNR API
    compileOnly("io.nightfish.lightnovelreader:api:0.4-SNAPSHOT")
    ksp("io.nightfish.lightnovelreader:compiler:0.4-SNAPSHOT")
}