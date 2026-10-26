plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

val versionFile: RegularFile = rootProject.layout.projectDirectory.file("VERSION")
val appVersionName: String =
    providers
        .fileContents(versionFile)
        .asText
        .get()
        .trim()

/**
 * Maps "major.minor.fix" to a monotonic versionCode: major * 10_000_000 + minor * 10_000 + fix.
 *
 * @throws IllegalArgumentException if the version is not three numbers or minor/fix overflow.
 */
fun versionCodeOf(versionName: String): Int {
    val majorWeight = 10_000_000
    val minorWeight = 10_000
    val parts = versionName.split(".")
    require(parts.size == 3) { "VERSION must be major.minor.fix, was '$versionName'" }
    val (major, minor, fix) =
        parts.map { part ->
            requireNotNull(part.toIntOrNull()) { "VERSION part '$part' is not a number" }
        }
    require(minor < majorWeight / minorWeight && fix < minorWeight) {
        "minor/fix out of range in '$versionName'"
    }
    return major * majorWeight + minor * minorWeight + fix
}

android {
    namespace = "dev.d1ito.dtox"
    compileSdk {
        version =
            release(36) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        applicationId = "dev.d1ito.dtox"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeOf(appVersionName)
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// LaunchWindowColorTest reads these XML files by path; declare them so edits re-run the tests.
tasks.withType<Test>().configureEach {
    inputs
        .files(fileTree("src/main/res") { include("values*/colors.xml") })
        .withPropertyName("launchWindowColors")
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.commonmark)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
