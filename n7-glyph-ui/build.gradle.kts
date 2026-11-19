plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

group = "com.github.i30mb1"
version = providers.gradleProperty("libVersion").getOrElse("0.5.0")

android {
    namespace = "n7.glyph.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric tests of ToyStage and GlyphHoldButton need the library's fonts and the test activity.
        unitTests.isIncludeAndroidResources = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    implementation(libs.lifecycle.runtime.compose)
    // Supporter: the one in-app purchase of every n7 app.
    implementation(libs.billing)
    // Billing pulls androidx.fragment 1.1.0 through Play services, Play Console flags it as outdated.
    implementation(libs.fragment)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "n7-glyph-ui"
            }
        }
    }
}
