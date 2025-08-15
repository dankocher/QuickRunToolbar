plugins {
    id("org.jetbrains.intellij") version "1.17.3"
    kotlin("jvm") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.dilongdann.quickrun"
version = "0.2.3"

repositories {
    mavenCentral()
}

intellij {
    type.set("IC") // Target IntelliJ IDEA Community; works for all IntelliJ-based IDEs
    // Usa una versión soportada por el plugin 1.x para evitar fallos en runIde
    version.set("2024.1")
    plugins.set(listOf())
}

tasks.patchPluginXml {
    // Compatibilidad: desde 241 hasta 252.* (tu IDE actual)
    sinceBuild.set("241")
    untilBuild.set("")
}

kotlin {
    jvmToolchain(17)
}

