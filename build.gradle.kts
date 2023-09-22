// This file typically defines the common versions of plugins used by modules in the project.
plugins {
  /**
   * Use `apply false` in the top-level build.gradle file to add a Gradle plugin as a build
   * dependency but not apply it to the current (root) project. Don't use `apply false` in
   * sub-projects. For more information, see Applying external plugins with same version to
   * subprojects.
   */

  // Gradle Plugin
  id("com.android.application") version "8.1.0" apply false
  id("com.android.library") version "8.1.0" apply false

  // Kotlin plugin
  // id("org.jetbrains.kotlin.android") version "1.8.22" apply false
  id("org.jetbrains.kotlin.android") version "1.9.0" apply false

  id("com.google.dagger.hilt.android") version "2.46.1" apply false
  id("androidx.navigation.safeargs") version "2.5.3" apply false
}
