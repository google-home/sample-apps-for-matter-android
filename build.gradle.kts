// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.0.2" apply false
    id("com.android.library") version "8.0.2" apply false
    id("org.jetbrains.kotlin.android") version "1.8.22" apply false
    id("com.google.dagger.hilt.android") version "2.46.1" apply false
    id("androidx.navigation.safeargs") version "2.5.3" apply false
}


//buildscript {
//    repositories {
//        google()
//        mavenCentral()
//        maven { url "https://plugins.gradle.org/m2/" }
//    }
//    dependencies {
//        classpath "com.android.tools.build:gradle:7.4.1"
//        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10"
//        classpath "com.google.protobuf:protobuf-java:3.17.2"
//        classpath "com.google.dagger:hilt-android-gradle-plugin:2.42"
//        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.5.3")
//        // NOTE: Do not place your application dependencies here; they belong
//        // in the individual module build.gradle files
//    }
//}

//task clean(type: Delete) {
//    delete rootProject.buildDir
//}


    val supportLibVersion = "1.4.1"
    val constraintLayoutVersion = "2.1.3"
    val coreVersion = "1.7.0"
    val coroutinesVersion = "1.6.0"
    val dataStoreVersion = "1.0.0"
    val materialVersion = "1.5.0"
    val protobufVersion = "3.19.4"
    val lifecycleVersion = "2.5.1"
    val lifecycleExtensionsVersion = "2.2.0"

    val runnerVersion = "1.4.0"
    val rulesVersion = "1.4.0"
    val junitVersion = "4.13.2"
    val espressoVersion = "3.4.0"

