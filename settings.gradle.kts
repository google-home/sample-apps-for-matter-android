// This settings file defines project-level repository settings and informs Gradle
// which modules it should include when building the app.
// Multi-module projects need to specify each module that should go into the final build.

pluginManagement {
  /**
   * The pluginManagement.repositories block configures the repositories Gradle uses to search or
   * download the Gradle plugins and their transitive dependencies. Gradle pre-configures support
   * for remote repositories such as JCenter, Maven Central, and Ivy. You can also use local
   * repositories or define your own remote repositories.
   */
  repositories {
    google()
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  /**
   * The dependencyResolutionManagement.repositories block is where you configure the repositories
   * and dependencies used by all modules in your project, such as libraries that you are using to
   * create your application. However, you should configure module-specific dependencies in each
   * module-level build.gradle file. For new projects, Android Studio includes Google's Maven
   * repository and the Maven Central Repository by default, but it does not configure any
   * dependencies (unless you select a template that requires some).
   */
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenLocal()
    mavenCentral()
  }
}

rootProject.name = "Google Home Mobile SDK Sample Apps"
include(":3p-ecosystem")
//include(":google-ecosystem")
