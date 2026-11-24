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
plugins {
    // Lets Gradle download the JDK toolchain below (JDK 27's jlink can't build android-36 system modules).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// Gradle still labels this API "incubating", but it is the standard Android project setup.
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// ---- Security floors for BUILD-TOOL libraries (OSV / Dependabot, 2026-11-22) ------------------------
// These libraries come in through the Android Gradle Plugin, Hilt/KSP annotation processors and the
// Unified Test Platform. None of them ship in the APK, but their old versions have known advisories,
// so any version below the floor is raised to it. Netty stays on its 4.1 line (what grpc expects).
// Kotlin Gradle plugin 2.2.10 (GHSA-r937-wjx7-w2jp) is not here: its fix only exists in a beta.
val securityFloors: Map<String, String> = mapOf(
    "io.netty:*" to "4.1.141.Final",
    "com.google.guava:guava" to "33.7.2-jre",
    "org.bouncycastle:bcprov-jdk18on" to "1.87",
    "org.bouncycastle:bcpkix-jdk18on" to "1.87",
    "org.bouncycastle:bcutil-jdk18on" to "1.87",
    "org.apache.commons:commons-lang3" to "3.22.0",
    "org.apache.httpcomponents:httpclient" to "4.5.14",
    "org.bitbucket.b_c:jose4j" to "0.9.7",
    "org.jdom:jdom2" to "2.0.6.1",
)

/** Numeric, segment-by-segment compare ("4.1.93.Final" < "4.1.141.Final", "32.0.1-jre" < "33.7.2-jre"). */
fun versionLess(a: String, b: String): Boolean {
    val pa = Regex("\\d+").findAll(a).map { it.value.toLong() }.toList()
    val pb = Regex("\\d+").findAll(b).map { it.value.toLong() }.toList()
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x < y
    }
    return false
}

fun ResolutionStrategy.applySecurityFloors() = eachDependency {
    val floor = securityFloors["${requested.group}:${requested.name}"]
        ?: securityFloors["${requested.group}:*"].takeIf { requested.name != "netty-tcnative-boringssl-static" && !requested.name.startsWith("netty-tcnative") }
        ?: return@eachDependency
    val current = requested.version ?: return@eachDependency
    if (versionLess(current, floor)) {
        useVersion(floor)
        because("security floor: $current has known advisories")
    }
}

// Applied to every project before it is evaluated: its plugin classpath (AGP, Kotlin, KSP, Hilt and
// what they pull in) and all its configurations (annotation processors, lint, test platform, app).
gradle.beforeProject {
    buildscript.configurations.configureEach { resolutionStrategy.applySecurityFloors() }
    configurations.configureEach { resolutionStrategy.applySecurityFloors() }
    // The two lines above miss "detached" configurations that AGP creates on the fly (lint, signing,
    // test platform), which still downloaded Guava 33.3.1 and Bouncy Castle 1.79. A metadata rule
    // rewrites the dependency edges of every component, so it applies to every resolution.
    dependencies.components.all { applySecurityFloors() }
    buildscript.dependencies.components.all { applySecurityFloors() }
}

/** Raises a library's dependencies on floored libraries, wherever that library is resolved. */
fun ComponentMetadataDetails.applySecurityFloors() = allVariants {
    withDependencies {
        forEach { dep ->
            val floor = securityFloors["${dep.group}:${dep.name}"]
                ?: securityFloors["${dep.group}:*"].takeIf { !dep.name.startsWith("netty-tcnative") }
                ?: return@forEach
            val current = dep.versionConstraint.requiredVersion.ifEmpty { dep.versionConstraint.preferredVersion }
            if (current.isNotEmpty() && versionLess(current, floor)) {
                dep.version { require(floor) }
                dep.because("security floor: $current has known advisories")
            }
        }
    }
}

rootProject.name = "SMS"
include(":app")
