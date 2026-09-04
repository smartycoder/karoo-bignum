import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing. The keystore and its passwords live outside the repository; see
// keystore.properties.template. Without that file the release build is simply left unsigned,
// so cloning the repo and running assembleRelease still works.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "io.smartycoder.bignum"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.smartycoder.bignum"
        // 26, not 29: the Karoo 2 runs Android 8.1 (API 27) and never goes further, and
        // nothing here needs more -- getFont() and fontVariationSettings are both API 26.
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Null when keystore.properties is absent, which leaves the APK unsigned rather
            // than failing the build.
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // The RELEASE key on a debug build, deliberately. The debug build exists to expose
            // the test-mode switch, which Settings.testMode gates on BuildConfig.DEBUG -- and
            // it carries the same applicationId as the release one. Signed with the default
            // debug key it cannot install over a release build at all: the signatures differ,
            // so `adb install -r` fails and the only way in is an uninstall, which takes the
            // rider's field layout and settings with it. Sharing the key keeps the debug build
            // a drop-in replacement in both directions.
            //
            // Null when keystore.properties is absent, which falls back to the ordinary debug
            // key rather than failing the build -- the same reasoning as release above.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    sourceSets["test"].kotlin.srcDirs("src/test/kotlin")
    sourceSets["androidTest"].kotlin.srcDirs("src/androidTest/kotlin")

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.karoo.ext)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // The header's type sizing is decided by real font metrics, which a JVM unit test cannot
    // see -- unitTests.isReturnDefaultValues hands back an empty Rect. It runs on the Karoo.
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
