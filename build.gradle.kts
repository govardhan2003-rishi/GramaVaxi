plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.24" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}

val externalBuildRoot = file("${System.getProperty("user.home")}/.gradle/grama-vaxi-build")
layout.buildDirectory.set(externalBuildRoot.resolve("root"))

subprojects {
    layout.buildDirectory.set(externalBuildRoot.resolve(name))
}
