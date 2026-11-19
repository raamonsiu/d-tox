import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

/** Values needed to sign the release build; they never live in the repository. */
data class ReleaseSigningValues(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

val keystoreProperties: Properties =
    Properties().apply {
        providers
            .fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
            .asText
            .orNull
            ?.let { contents -> load(StringReader(contents)) }
    }

/**
 * Reads one signing value: the environment variable wins over the ignored `keystore.properties`.
 *
 * @return the non-blank value, or null when neither source defines it.
 */
fun signingValue(
    environmentVariable: String,
    propertyKey: String,
): String? =
    providers.environmentVariable(environmentVariable).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }

/**
 * Collects the release signing values from `DTOX_*` environment variables or `keystore.properties`.
 *
 * @return the values when all four are defined (a relative keystore path resolves against the
 *   repository root), or null so the release build stays unsigned instead of failing.
 */
fun releaseSigningValuesOrNull(): ReleaseSigningValues? {
    val storeFilePath = signingValue("DTOX_KEYSTORE_FILE", "storeFile")
    val storePassword = signingValue("DTOX_KEYSTORE_PASSWORD", "storePassword")
    val keyAlias = signingValue("DTOX_KEY_ALIAS", "keyAlias")
    val keyPassword = signingValue("DTOX_KEY_PASSWORD", "keyPassword")
    val requiredValues = listOf(storeFilePath, storePassword, keyAlias, keyPassword)
    val definedValueCount = requiredValues.count { it != null }
    if (definedValueCount in 1..<requiredValues.size) {
        logger.warn("Release signing is incomplete; the release build will be unsigned.")
    }
    if (storeFilePath == null || storePassword == null || keyAlias == null || keyPassword == null) {
        return null
    }
    return ReleaseSigningValues(
        storeFile = rootProject.file(storeFilePath),
        storePassword = storePassword,
        keyAlias = keyAlias,
        keyPassword = keyPassword,
    )
}

val releaseSigningValues: ReleaseSigningValues? = releaseSigningValuesOrNull()

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

    signingConfigs {
        releaseSigningValues?.let { values ->
            create("release") {
                storeFile = values.storeFile
                storePassword = values.storePassword
                keyAlias = values.keyAlias
                keyPassword = values.keyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
