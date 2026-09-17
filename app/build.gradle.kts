import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = providers.environmentVariable("RAILBRAKE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("RAILBRAKE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RAILBRAKE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RAILBRAKE_KEY_PASSWORD").orNull

android {
    namespace = "ru.railbrake.calculator"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.railbrake.calculator"
        minSdk = 26
        targetSdk = 34
        versionCode = 143
        versionName = "1.2.2-dev15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (
            releaseKeystorePath != null &&
            releaseStorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

val launcherIconSource = rootProject.file("design/launcher_icon.webp.b64").let { primary ->
    if (primary.isFile) primary else rootProject.file("patch/design/launcher_icon.webp.b64")
}
val launcherIconOutput = layout.projectDirectory.file("src/main/res/drawable-nodpi/ic_launcher_art.webp").asFile
val generateLauncherIcon = tasks.register("generateLauncherIcon") {
    inputs.file(launcherIconSource)
    outputs.file(launcherIconOutput)
    doLast {
        check(launcherIconSource.isFile) { "Launcher icon source not found: ${launcherIconSource.absolutePath}" }
        launcherIconOutput.parentFile.mkdirs()
        val encoded = launcherIconSource.readText().filterNot(Char::isWhitespace)
        launcherIconOutput.writeBytes(Base64.getDecoder().decode(encoded))
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(generateLauncherIcon) }
