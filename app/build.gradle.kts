plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

android {
    namespace = "com.webwithroni.voicejarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.webwithroni.voicejarvis"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
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

    signingConfigs {
        if (
            System.getenv("CI") == "true" &&
            System.getenv("VOICE_JARVIS_CI_KEYSTORE_PATH") != null
        ) {
            create("ciDebug") {
                storeFile =
                    file(
                        System.getenv(
                            "VOICE_JARVIS_CI_KEYSTORE_PATH"
                        )
                    )

                storePassword =
                    System.getenv(
                        "VOICE_JARVIS_CI_STORE_PASSWORD"
                    )

                keyAlias =
                    "voice-jarvis-ci"

                keyPassword =
                    System.getenv(
                        "VOICE_JARVIS_CI_STORE_PASSWORD"
                    )
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false

            if (
                System.getenv("CI") == "true" &&
                System.getenv("VOICE_JARVIS_CI_KEYSTORE_PATH") != null
            ) {
                signingConfig =
                    signingConfigs.getByName(
                        "ciDebug"
                    )
            }
        }

        release {
            isMinifyEnabled = false
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
}

/*
 * Kotlin 2.3 compiler configuration.
 *
 * Keep this outside the Android android {} block.
 */
kotlin {
    compilerOptions {
        jvmTarget =
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(
        "androidx.test.ext:junit:1.2.1"
    )

    androidTestImplementation(
        "androidx.test:runner:1.6.2"
    )


    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
}
