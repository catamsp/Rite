plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val baseVersion = "2.0"

android {
    namespace = "com.catamsp.rite"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.catamsp.rite"
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
                ?: project.findProperty("KEYSTORE_FILE") as String?
                ?: ""
            storeFile = if (keystoreFile.isNotEmpty()) file(keystoreFile) else null
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: project.findProperty("KEYSTORE_PASSWORD") as String?
                ?: ""
            keyAlias = System.getenv("KEY_ALIAS")
                ?: project.findProperty("KEY_ALIAS") as String?
                ?: ""
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: project.findProperty("KEY_PASSWORD") as String?
                ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.material:material-icons-extended")
}
