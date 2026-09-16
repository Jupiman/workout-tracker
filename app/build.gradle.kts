plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.jupiman.workouttracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jupiman.workouttracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 17000
        versionName = "1.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_SELF_HOSTED_HTTP", "true")
            manifestPlaceholders["selfHostedCleartextPermitted"] = "true"
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_SELF_HOSTED_HTTP", "false")
            manifestPlaceholders["selfHostedCleartextPermitted"] = "false"
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")

            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.getByName("release").takeIf { it.storeFile != null }?.let {
                signingConfig = it
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation(project(":wear-protocol"))

    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
