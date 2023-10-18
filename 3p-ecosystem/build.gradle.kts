// Module-level build file.
// This file t lets you configure build settings for the specific module it is located in.
// Configuring these build settings lets you provide custom packaging options,
// such as additional build types and product flavors, and override settings in the
// main/ app manifest or top-level build script.

/**
 * The first section in this file applies among other things the Android Gradle plugin
 * to this build and makes the android block available to specify
 * Android-specific build options.
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf") version "0.9.1"
    id("org.jetbrains.kotlin.kapt")
    id("dagger.hilt.android.plugin")
    id("androidx.navigation.safeargs")
    id("com.google.dagger.hilt.android")
    id("com.ncorti.ktfmt.gradle") version "0.12.0"
}

/**
 * Locate (and possibly download) a JDK used to build your kotlin
 * source code. This also acts as a default for sourceCompatibility,
 * targetCompatibility and jvmTarget. Note that this does not affect which JDK
 * is used to run the Gradle build itself, and does not need to take into
 * account the JDK version required by Gradle plugins (such as the
 * Android Gradle Plugin)
 */
kotlin {
    jvmToolchain(17)
}

/**
 * The android block is where you configure all your Android-specific
 * build options.
 */
android {
    /**
     * The app's namespace. Used primarily to access app resources.
     */
    namespace = "com.google.homesampleapp"

    /**
     * compileSdk specifies the Android API level Gradle should use to
     * compile your app. This means your app can use the API features included in
     * this API level and lower.
     */
    compileSdk = 33

    /**
     * The defaultConfig block encapsulates default settings and entries for all
     * build variants and can override some attributes in main/AndroidManifest.xml
     * dynamically from the build system. You can configure product flavors to override
     * these values for different versions of your app.
     */
    defaultConfig {
        // Uniquely identifies the package for publishing.
        applicationId = "com.google.homesampleapp"

        // Defines the minimum API level required to run the app.
        minSdk = 27

        // Specifies the API level used to test the app.
        targetSdk = 33

        // Defines the version number of your app.
        versionCode = 16

        // Defines a user-friendly version name for your app.
        versionName = "1.4.2"

        // Test Runner.
        testInstrumentationRunner = "com.google.homesampleapp.CustomTestRunner"
    }

    /**
     * The buildTypes block is where you can configure multiple build types.
     * By default, the build system defines two build types: debug and release. The
     * debug build type is not explicitly shown in the default build configuration,
     * but it includes debugging tools and is signed with the debug key. The release
     * build type applies ProGuard settings and is not signed by default.
     */
    buildTypes {
        /**
         * By default, Android Studio configures the release build type to enable code
         * shrinking, using minifyEnabled, and specifies the default ProGuard rules file.
         */
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro")
        }
    }

    // FIXME
    /**
     * To override source and target compatibility (if different from the
     * toolchain JDK version), add the following. All of these
     * default to the same value as kotlin.jvmToolchain. If you're using the
     * same version for these values and kotlin.jvmToolchain, you can
     * remove these blocks.
     */
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        dataBinding = true
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(listOf("third_party/connectedhomeip/libs/jniLibs"))
        }
    }
    android.buildFeatures.viewBinding = true

    /**
     * The productFlavors block is where you can configure multiple product flavors.
     * This lets you create different versions of your app that can
     * override the defaultConfig block with their own settings. Product flavors
     * are optional, and the build system does not create them by default.
     *
     * For example, one can create a free and paid product flavor. Each product flavor
     * then specifies its own application ID, so that they can exist on the Google
     * Play Store, or an Android device, simultaneously.
     *
     * If you declare product flavors, you must also declare flavor dimensions
     * and assign each flavor to a flavor dimension.
     */
    flavorDimensions += "version"
    productFlavors {
        create("default") {
            dimension = "version"
            applicationIdSuffix = ".default"
            versionNameSuffix = "-default"
        }
        create("targetcommissioner") {
            dimension = "version"
            applicationIdSuffix = ".targetcommissioner"
            versionNameSuffix = "-targetcommissioner"
        }
    }
    // Gradle will use the NDK that"s associated by default with its plugin.
    // If it"s not available (from the SDK Manager), then stripping the .so"s will not happen
    // (message: Unable to strip library...)
    // See https://github.com/google-home/sample-app-for-matter-android/issues/82.
    // https://developer.android.com/studio/projects/install-ndk
    // If you want to use a specific NDK, then uncomment the statement below with the proper
    // NDK version.
    // ndkVersion = "25.2.9519653"
}

dependencies {
    // Native libs
    implementation(fileTree(mapOf("dir" to "third_party/connectedhomeip/libs", "include" to listOf("*.jar", "*.so"))))

    // Connected Home
    implementation(libs.play.services.base)
    implementation(libs.play.services.home)

    // Thread Network
    implementation(libs.play.services.threadnetwork)
    // Thread QR Code Scanning
    implementation(libs.code.scanner)
    // Thread QR Code Generation
    implementation(libs.zxing)


    // AndroidX
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.core.ktx)
    implementation(libs.databinding.runtime)
    implementation(libs.legacy.support.v4)
    implementation(libs.preference)

    // Navigation
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    // Lifecycle
    implementation(libs.lifecycle.extensions)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    // Datastore
    implementation(libs.datastore)
    implementation(libs.datastore.core)
    implementation(libs.protobuf.javalite)

    // Hilt
    // https://dagger.dev/hilt/gradle-setup
    // TODO: Upgrade to KSP when supported by Hilt/Dagger.
    //      https://developer.android.com/build/migrate-to-ksp#replace-annotation
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.core)


    // Hilt For instrumentation tests
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)

    // Task.await()
    implementation(libs.kotlinx.coroutines.play.services)

    // Other
    implementation(libs.material)
    implementation(libs.timber)
    // Needed for using BaseEncoding class
    implementation(libs.guava)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.espresso.contrib)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.rules)
    androidTestImplementation(libs.uiautomator)
}

// Issue with androidx.test.espresso:espresso-contrib:3.5.1
// https://github.com/android/android-test/issues/999
configurations.configureEach {
    exclude(group = "com.google.protobuf", module = "protobuf-lite")
}

kapt {
    correctErrorTypes = true
}

protobuf {
    protoc {
        // For Apple M1 Chip
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        val protocDepSuffix = if (isMac) ":osx-x86_64" else ""
        artifact = "com.google.protobuf:protoc:3.14.0" + protocDepSuffix
    }

    // Generates the java Protobuf-lite code for the Protobufs in this project. See
    // https://github.com/google/protobuf-gradle-plugin#customizing-protobuf-compilation
    // for more information.
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}