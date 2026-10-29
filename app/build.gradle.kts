import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun prop(name: String, fallback: String = ""): String = (project.findProperty(name) as String?) ?: fallback

android {
    namespace = "com.arya.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arya.companion"
        minSdk = 30 // Android 11 and newer
        targetSdk = 36 // Android 16: required by Google Play for new apps and updates since 31 Aug 2026
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.2.0"

        buildConfigField("String", "REPORT_EMAIL", "\"${prop("aryaReportEmail")}\"")
        buildConfigField("String", "REPORT_URL", "\"${prop("aryaReportUrl")}\"")
        buildConfigField("String", "PRIVACY_URL", "\"${prop("aryaPrivacyUrl")}\"")
    }

    // Release signing comes from environment variables (see .github/workflows/android.yml); never commit a keystore.
    val keystorePath = System.getenv("ARYA_KEYSTORE_PATH")
    if (keystorePath != null && file(keystorePath).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ARYA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ARYA_KEY_ALIAS")
                keyPassword = System.getenv("ARYA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
