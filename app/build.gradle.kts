plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.webwithroni.voicejarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.webwithroni.voicejarvis"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.4.0"

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\""
        )

        buildConfigField(
            "String",
            "TAVILY_API_KEY",
            "\"${project.findProperty("TAVILY_API_KEY") ?: ""}\""
        )

        buildConfigField(
            "String",
            "GROQ_API_KEY",
            "\"${project.findProperty("GROQ_API_KEY") ?: ""}\""
        )

        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${project.findProperty("OPENROUTER_API_KEY") ?: ""}\""
        )

        buildConfigField(
            "String",
            "DEEPSEEK_API_KEY",
            "\"${project.findProperty("DEEPSEEK_API_KEY") ?: ""}\""
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
}
