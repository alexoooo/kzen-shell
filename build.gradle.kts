//import org.springframework.boot.gradle.tasks.bundling.BootJar


plugins {
    kotlin("jvm") version kotlinVersion
//    kotlin("plugin.spring") version kotlinVersion
//    id("org.springframework.boot") version springBootVersion
//    id("io.spring.dependency-management") version dependencyManagementVersion
}


group = "tech.kzen"
version = "0.26.1"


repositories {
//    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    mavenCentral()
    mavenLocal()
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }
}


dependencies {
    implementation(kotlin("reflect"))

//    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlin")

    implementation("com.google.guava:guava:$guavaVersion")
    implementation("io.github.microutils:kotlin-logging:$kotlinLogging")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
//    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

//    testImplementation("org.springframework.boot:spring-boot-starter-test")
//    testImplementation("io.projectreactor:reactor-test")
}


tasks {
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = jvmTargetVersion
        }
    }
}


tasks.compileJava {
    options.release.set(javaVersion)
}

//tasks.getByName<BootJar>("bootJar") {
//    archiveClassifier.set("boot")
//}


val dependenciesDir = "dependencies"
task("copyDependencies", Copy::class) {
    from(configurations.default).into("$buildDir/libs/$dependenciesDir")
}


tasks.getByName<Jar>("jar") {
    val jvmProject = project(":")
    val copyDependenciesTask = jvmProject.tasks.getByName("copyDependencies") as Copy
    dependsOn(copyDependenciesTask)

    manifest {
        attributes["Main-Class"] = "tech.kzen.shell.KzenShellMainKt"
        attributes["Class-Path"] = configurations
            .runtimeClasspath
            .get()
            .joinToString(separator = " ") { file ->
                "$dependenciesDir/${file.name}"
            }
    }
}
