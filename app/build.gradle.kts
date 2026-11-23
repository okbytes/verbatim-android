import java.io.File
import java.util.Properties

plugins {
    id("com.android.application") version "8.9.2"
    id("org.jetbrains.kotlin.android") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

val signingPropsFile =
    listOf(
            File(System.getProperty("user.home"), ".config/verbatim/signing/keystore.properties"),
            rootProject.file(".tools/signing/keystore.properties"),
            rootProject.file("keystore.properties"),
        )
        .firstOrNull { it.isFile }
val signingProps =
    Properties().apply {
        if (signingPropsFile != null) {
            signingPropsFile.inputStream().use(::load)
        }
    }

fun signingValue(propKey: String, envKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: signingProps.getProperty(propKey)?.takeIf { it.isNotBlank() }

val releaseStorePath = signingValue("storeFile", "VERBATIM_KEYSTORE_PATH")
val releaseStorePassword = signingValue("storePassword", "VERBATIM_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "VERBATIM_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "VERBATIM_KEY_PASSWORD")
val hasReleaseSigning =
    releaseStorePath != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

android {
    namespace = "app.verbatim"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.verbatim.android"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val resolved =
                    if (File(releaseStorePath!!).isAbsolute) File(releaseStorePath)
                    else rootProject.file(releaseStorePath)
                storeFile = resolved
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
