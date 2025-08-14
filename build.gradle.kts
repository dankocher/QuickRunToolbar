plugins {
    id("org.jetbrains.intellij") version "1.17.3"
    kotlin("jvm") version "2.1.0"
}

group = "com.dilongdann.quickrun"
version = "0.1.11"

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
    untilBuild.set("252.*")
}

kotlin {
    jvmToolchain(17)
}

// Optional: run IDE for manual testing
tasks.runIde {
    autoReloadPlugins.set(true)
}

// No definimos SearchableConfigurable, desactivar generación de opciones de búsqueda
tasks.buildSearchableOptions {
    enabled = false
}