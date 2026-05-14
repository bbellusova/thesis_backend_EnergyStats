val kotlinVersion: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.dataframe")
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.4.0"
    kotlin("plugin.allopen")
    id("io.kotest").version("6.1.11")
}

group = "cvut.fit.energyStats"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // Source: https://mvnrepository.com/artifact/io.kotest/kotest-runner-junit5-jvm
    testImplementation("io.kotest:kotest-runner-junit5-jvm:6.1.11")
    testImplementation("io.kotest:kotest-extensions-spring:6.1.11")

    implementation("org.jetbrains.kotlinx:dataframe:1.0.0-Beta4")

    // Source: https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
    implementation("org.jetbrains.exposed:exposed-core:1.1.1")

    // Source: https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-jdbc
    implementation("org.jetbrains.exposed:exposed-jdbc:1.1.1")

    implementation("org.jetbrains.exposed:exposed-spring-boot4-starter:1.1.1")


    // Source: https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-kotlin-datetime
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.1.1")

    implementation("org.postgresql:postgresql:42.7.8")

    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // Source: https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-web
    implementation("org.springframework.boot:spring-boot-starter-web:4.0.5")

    // Source: https://mvnrepository.com/artifact/io.swagger.core.v3/swagger-annotations
    implementation("io.swagger.core.v3:swagger-annotations:2.2.44")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Source: https://mvnrepository.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")

    // Source: https://mvnrepository.com/artifact/io.mockk/mockk
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("com.h2database:h2:2.3.232")

    // Source: https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-security
    implementation("org.springframework.boot:spring-boot-starter-security:4.0.5")

    // Source: https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-test
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.5")

    // Source: https://mvnrepository.com/artifact/org.springframework.security/spring-security-core
    implementation("org.springframework.security:spring-security-core:7.0.4")

    // Source: https://mvnrepository.com/artifact/com.diffplug.spotless/spotless-plugin-gradle
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.4.0")

    // Source: https://mvnrepository.com/artifact/org.reactivestreams/reactive-streams
    implementation("org.reactivestreams:reactive-streams:1.0.4")

    // Source: https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-reactor
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")

    // Source: https://mvnrepository.com/artifact/com.github.ulisesbocchio/jasypt-spring-boot-starter
    implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:4.0.4")


}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<Test>().configureEach {
    logger.lifecycle("UP-TO-DATE check for $name is disabled, forcing it to run.")
    outputs.upToDateWhen { false }
}

kotlin {
    jvmToolchain(21)
}

allOpen {
    annotations(
        "org.springframework.stereotype.Component",
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository",
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.context.annotation.Configuration",
        "cvut.fit.energyStats.config.IsSiteOwner"
    )
}

spotless {
    kotlin {
        ktfmt("0.62")
        ktlint()
    }
}
