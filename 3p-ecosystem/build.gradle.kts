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

android {
    namespace = "com.google.homesampleapp"
    compileSdk = 33

    defaultConfig {
        // DeviceSharingClone: Change this value for the target commissioner cloned app
        applicationId = "com.google.homesampleapp"
        minSdk = 27
        targetSdk = 33
        versionCode = 15
        versionName = "1.4.1"
        testInstrumentationRunner = "com.google.homesampleapp.CustomTestRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro")
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
        dataBinding = true
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(listOf("third_party/connectedhomeip/libs/jniLibs"))
        }
    }
    android.buildFeatures.viewBinding = true

    // Specifies one flavor dimension.
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
    implementation("com.google.android.gms:play-services-base:18.2.0")
    implementation("com.google.android.gms:play-services-home:16.0.0")

    // AndroidX
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.databinding:databinding-runtime:8.0.2")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.preference:preference:1.2.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.6.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.6.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")

    // Datastore
    implementation("androidx.datastore:datastore:1.0.0")
    implementation("androidx.datastore:datastore-core:1.0.0")
    implementation("com.google.protobuf:protobuf-javalite:3.18.0")

    // Hilt
    // https://dagger.dev/hilt/gradle-setup
    implementation("com.google.dagger:hilt-android:2.46.1")
    kapt("com.google.dagger:hilt-compiler:2.46.1")
    implementation("com.google.ar:core:1.38.0")


    // Hilt For instrumentation tests
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.44.2")
    kaptAndroidTest("com.google.dagger:hilt-compiler:2.46.1")

    // Task.await()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")

    // Other
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
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
        val isMac = System.getProperty("os.name").toLowerCase().contains("mac")
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