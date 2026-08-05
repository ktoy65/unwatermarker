import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

val signingPropertyNames = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val signingStoreFile = keystoreProperties.getProperty("storeFile")?.let(rootProject::file)
val hasSigning = signingPropertyNames.all { !keystoreProperties.getProperty(it).isNullOrBlank() }
        && signingStoreFile?.isFile == true
val releaseRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}

if (releaseRequested && !hasSigning) {
    throw GradleException("Release signing is not fully configured in keystore.properties")
}

android {
    namespace = "com.unwatermarker"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.3"
    }

    signingConfigs {
        create("release") {
            if (hasSigning) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            signingConfig = if (hasSigning) signingConfigs["release"] else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
}
