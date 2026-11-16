pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io") {
            content { includeGroup("com.github.i30mb1") }
        }
    }
}

// Shared n7 Glyph look and toy kit (https://github.com/i30mb1/n7-glyph-ui).
// A local checkout is used directly; without one the JitPack artifact is downloaded. Looked for in this order:
//   ../n7-glyph-ui  an app next to the library checkout
//   ..              this project kept inside the library repo itself
val localGlyphUi = listOf(file("../n7-glyph-ui"), file(".."))
    .firstOrNull { it.resolve("settings.gradle.kts").exists() && it.resolve("n7-glyph-ui/build.gradle.kts").exists() }
if (localGlyphUi != null) {
    includeBuild(localGlyphUi) {
        dependencySubstitution {
            substitute(module("com.github.i30mb1:n7-glyph-ui")).using(project(":n7-glyph-ui"))
        }
    }
}

rootProject.name = "Glyph Template"
include(":app")
