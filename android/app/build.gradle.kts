import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Firebase plugins: apply only when google-services.json is present.
    // The google-services plugin must come before crashlytics.
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.sportage.obd"
    compileSdk = 35

    // Release signing credentials are read from the environment first (CI) and then from
    // android/keystore.properties (local machines). Neither is ever committed.
    // With no credentials at all the release build stays unsigned instead of failing, so
    // ordinary local builds keep working without a keystore.
    val keystoreProps = Properties()
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { keystoreProps.load(it) }
    }

    val envKeystorePath = System.getenv("SPORTAGE_KEYSTORE_FILE")
    val releaseStoreFile = when {
        !envKeystorePath.isNullOrBlank() -> rootProject.file(envKeystorePath)
        !keystoreProps.getProperty("storeFile").isNullOrBlank() ->
            rootProject.file(keystoreProps.getProperty("storeFile")!!)
        else -> null
    }
    val releaseStorePassword =
        System.getenv("SPORTAGE_KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
    val releaseKeyAlias =
        System.getenv("SPORTAGE_KEY_ALIAS") ?: keystoreProps.getProperty("keyAlias")
    val releaseKeyPassword =
        System.getenv("SPORTAGE_KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword")

    val hasReleaseKeystore = releaseStoreFile?.exists() == true &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    logger.lifecycle(
        if (hasReleaseKeystore) {
            "Release signing: ENABLED (${releaseStoreFile!!.name})"
        } else {
            "Release signing: no credentials - assembleRelease will produce an UNSIGNED apk"
        }
    )

    defaultConfig {
        applicationId = "com.sportage.obd"
        minSdk = 28 // Android 9+ as specified in PRD Section 1.1
        targetSdk = 35
        versionCode = 15
        versionName = "1.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // minSdk is 28, so APK Signature Scheme v2 alone is enough; JAR (v1) signing
                // only matters below Android 7.0 and AGP does not emit it above minSdk 24.
                enableV1Signing = false
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
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        // After a release build, upload ProGuard/R8 mapping files to Crashlytics
        // so crash reports are deobfuscated.
        // Command: ./gradlew uploadCrashlyticsMappingFileRelease
        // This requires Google Services JSON and network access to Firebase.
        tasks.register("uploadCrashlyticsMapping") {
            dependsOn(tasks.named("bundleRelease"))
            doLast {
                logger.lifecycle("To upload mappings: ./gradlew uploadCrashlyticsMappingFileRelease")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }

    // R8 writes the release mapping to app/build/outputs/mapping/release/mapping.txt.
    // Do not call applicationVariants.all here: Kotlin resolves that as
    // Iterable.all (Boolean predicate) and the script fails to compile.
}

// Skip Crashlytics upload tasks unless CI explicitly opts in. The plugin otherwise
// fails the build when it cannot reach Firebase (no network, placeholder
// google-services.json). Plugin 2.9.9 has no CrashlyticsUploadDistributionTask type;
// match the tasks it actually registers.
tasks.matching {
    it.name.startsWith("uploadCrashlyticsMappingFile") ||
        it.name.startsWith("uploadCrashlyticsSymbolFile")
}.configureEach {
    enabled = providers.environmentVariable("UPLOAD_CRASHLYTICS_MAPPINGS").isPresent
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.kotlinx.coroutines.android)
    implementation("org.mapsforge:mapsforge-map-android:0.25.0")
    implementation("org.mapsforge:mapsforge-map-reader:0.25.0")
    // Kotlin 2.0-compatible native release; do not bypass Kotlin metadata checks.
    implementation("org.maplibre.gl:android-sdk:12.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // MediaBrowserService connection for real now-playing sync with installed music apps.
    implementation("androidx.media:media:1.7.0")

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Firebase: use the BOM to keep versions aligned. Analytics is always-on,
    // Crashlytics is also always-on in this app. Both are free tier friendly.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Track key user flows: OBD connection success/failure, scan completions,
    // launcher usage, onboarding completion. Analytics respects no special
    // consent here because the data is anonymous and non-tracking.
    // (Future: add a user consent gate around analytics if required by policy.)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
