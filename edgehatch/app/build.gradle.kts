import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional release signing. Reads edgehatch/keystore.properties (git-ignored)
// or the matching EDGEHATCH_* environment variables. No keystore or secret is
// committed; when none is configured the release build is produced unsigned.
// See README.md ("Signing & release") for the keytool/apksigner steps.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val releaseStoreFile = keystoreProps.getProperty("storeFile") ?: System.getenv("EDGEHATCH_STORE_FILE")
val releaseStorePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("EDGEHATCH_STORE_PASSWORD")
val releaseKeyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("EDGEHATCH_KEY_ALIAS")
val releaseKeyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("EDGEHATCH_KEY_PASSWORD")
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "app.edgehatch.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.edgehatch.launcher"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed only when a keystore is configured (see above); otherwise
            // the release APK is unsigned.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
