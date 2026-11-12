pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  versionCatalogs {
    create("kotlinx") { from(files("gradle/kotlinx.versions.toml")) }
    create("androidx") { from(files("gradle/androidx.versions.toml")) }
    create("compose") { from(files("gradle/compose.versions.toml")) }
  }
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    google()
    maven { setUrl("https://jitpack.io") }
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Neko"

// Windows-only port: `-PdesktopOnly` skips the Android modules, so no Android SDK is needed.
// They stay in the repo as the source to port from.
val desktopOnly =
    providers.gradleProperty("desktopOnly").map { it != "false" }.getOrElse(false)

if (!desktopOnly) {
  include(":app")

  include(":core")

  include(":constants")

  include(":baselineprofile")
}
