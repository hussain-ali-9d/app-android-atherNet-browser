import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { stream ->
        localProperties.load(stream)
    }
}

android {
    namespace = "com.jhaiian.clint"
    compileSdk = 37

    defaultConfig {
        // AetherNet identity. `namespace` stays com.jhaiian.clint: the prebuilt adblock and website-blocker
        // libraries export JNI symbols bound to that Kotlin package.
        applicationId = "com.aethernet.browser"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"

        // Cruise VPN backend (shared with OneApp VPN). Play Integrity tokens are minted for this
        // Google Cloud project; the backend decodes them with the same project's credentials.
        buildConfigField("String", "VPN_CLOUD_PROJECT_NUMBER", "\"456305844172\"")
    }

    val hasSigningConfig = localProperties.getProperty("signingConfig.storePassword") != null

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(localProperties.getProperty("signingConfig.storeFile", "app/release_keystore.jks"))
                storePassword = localProperties.getProperty("signingConfig.storePassword")
                keyAlias = localProperties.getProperty("signingConfig.keyAlias")
                keyPassword = localProperties.getProperty("signingConfig.keyPassword")
            }
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FDROID", "false")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FDROID", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningConfig) {
                signingConfig = signingConfigs["release"]
            }
            buildConfigField("String", "VPN_API_BASE_URL", "\"https://api.quadrupleapps.com\"")
            buildConfigField("boolean", "VPN_SKIP_KEY_ATTESTATION", "false")
        }
        debug {
            // No suffix: the VPN backend's Play Integrity check expects this exact package.
            isDebuggable = true
            // Production host, as in OneApp's debug build; staging is https://api-stag.quadrupleapps.com
            buildConfigField("String", "VPN_API_BASE_URL", "\"https://cv-api-prod-de-01.quadrupleapps.com\"")
            // TODO(vpn-attestation): set back to false once DevOps has onboarded
            // com.aethernet.browser (package + signing-cert digest) on the VPN backend. Debug only:
            // sends no key attestation, for while that gate is waived. Release always attests.
            buildConfigField("boolean", "VPN_SKIP_KEY_ATTESTATION", "true")
        }
    }

    val hasNativeLibs = fileTree("src/main/jniLibs") { include("**/*.so") }.files.isNotEmpty() ||
            file("src/main/cpp/CMakeLists.txt").exists() ||
            file("CMakeLists.txt").exists() ||
            file("src/main/jni/Android.mk").exists()

    splits {
        abi {
            isEnable = hasNativeLibs
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.webkit:webkit:1.17.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    implementation("com.google.android.play:integrity:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("io.github.amrdeveloper:codeview:1.3.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("com.j256.simplemagic:simplemagic:1.17")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.material3.adaptive:adaptive-layout")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation")
    implementation("androidx.activity:activity-compose:1.13.0")
}
