
pluginManagement {
    val kotlinVersion = "2.3.20"
    plugins {
        kotlin("jvm") version kotlinVersion
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
        kotlin("plugin.allopen") version kotlinVersion
        kotlin("plugin.dataframe") version kotlinVersion
    }
}
rootProject.name = "BP_EnergyStats"

