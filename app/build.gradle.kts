plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---- Versioning ------------------------------------------------------------------------------
// version.properties holds versionName (MAJOR.MINOR.PATCH); CI may override it with -PappVersionName.
// versionCode = MAJOR*1_000_000 + MINOR*1_000 + PATCH, so every bump increases it (Play requirement).
val appVersionName: String = providers.gradleProperty("appVersionName").orNull
    ?: rootProject.file("version.properties").readLines()
        .firstNotNullOfOrNull { Regex("""^\s*versionName\s*=\s*(\S+)\s*$""").find(it)?.groupValues?.get(1) }
    ?: throw GradleException("version.properties has no versionName")
val appVersionCode: Int = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})""").matchEntire(appVersionName)
    ?.destructured?.let { (major, minor, patch) -> major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt() }
    ?: throw GradleException("versionName '$appVersionName' must be MAJOR.MINOR.PATCH, e.g. 1.2.3")

// ---- Release signing (S1) --------------------------------------------------------------------------
// Only from environment variables (CI secrets or your shell); never commit a keystore or password.
val signingStore = providers.environmentVariable("SMS_KEYSTORE_FILE").orNull
val signingReady = signingStore != null && file(signingStore).exists()

android {
    namespace = "com.dns.sms"
    compileSdk {
        // 37.1: required by AndroidX core 1.19 / Compose 1.13 / Lifecycle 2.11 (compile-time only; targetSdk unchanged).
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.dns.sms"
        minSdk = 30
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // English (US) only for now: drops library translations (AndroidX, Material, Play services) so
    // dialogs and pickers never mix languages. Add locales here when translations land.
    androidResources {
        @Suppress("UnstableApiUsage") // replaces the deprecated resourceConfigurations
        localeFilters += listOf("en", "en-rUS")
    }

    signingConfigs {
        if (signingReady) create("release") {
            storeFile = file(signingStore!!)
            storePassword = providers.environmentVariable("SMS_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("SMS_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("SMS_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        release {
            // Unsigned when no keystore is configured (local builds, CI without secrets).
            if (signingReady) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            // FEAT-2: phones only. x86/x86_64 native libs (13 MB of SQLCipher) exist only for emulators.
            // Play App Bundles (./gradlew bundleRelease) already deliver one ABI per device.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Phones only (FEAT-2): x86_64 adds ~13 MB of SQLCipher for ChromeOS/emulators, which aren't targets.
        disable += "ChromeOsAbiSupport"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    // Gradle itself runs on JDK 27. Compilation must use JDK 21: JDK 27's jlink refuses Android's
    // core-for-system-modules.jar ("cannot find the build signature in the java.base"), which AGP
    // needs for compileSdk 36. Revisit when AGP supports JDK 27 (see Requirement.md section 3).
    // S17: pinned to Eclipse Adoptium (Temurin) so a different vendor's JDK is never picked or downloaded.
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android.sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Installs the baseline profile (src/main/baseline-prof.txt + library profiles) on sideloaded installs too.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    // Room's migration/testing code needs 1.8.x; Navigation alone would resolve 1.7.3 and the
    // androidTest classpath is pinned to the app's versions (AbstractMethodError in MigrationTestHelper).
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android) { artifact { type = "aar" } }
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.auth.blockstore)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}

// FEAT-2: fail if the release APK grows past the budget (raise it deliberately, with a note in Requirement.md).
tasks.register("checkReleaseSize") {
    dependsOn("assembleRelease")
    doLast {
        val apk = layout.buildDirectory.dir("outputs/apk/release").get().asFile
            .listFiles { f -> f.extension == "apk" }?.maxByOrNull { it.lastModified() }
            ?: throw GradleException("No release APK found")
        val mb = apk.length() / 1_000_000.0
        val budgetMb = 16.0 // measured 15.1 MB on 2026-11-21
        println("Release APK: %.1f MB (budget %.1f MB)".format(mb, budgetMb))
        if (mb > budgetMb) throw GradleException("Release APK is %.1f MB, over the %.1f MB budget".format(mb, budgetMb))
    }
}

// Prints the version for CI and scripts: ./gradlew -q :app:printVersion
tasks.register("printVersion") {
    val name = appVersionName
    val code = appVersionCode
    doLast { println("versionName=$name\nversionCode=$code") }
}
