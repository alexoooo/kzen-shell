import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    kotlin("jvm") version kotlinVersion
}


group = "tech.kzen"
version = "0.29.1-SNAPSHOT"


repositories {
    mavenCentral()
    mavenLocal()
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
    }
}


dependencies {
    implementation(kotlin("reflect"))

    implementation("tools.jackson.module:jackson-module-kotlin:$jacksonModuleKotlin")

    implementation("com.google.guava:guava:$guavaVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
//    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
}


//tasks {
//    compileKotlin {
//        kotlinOptions {
//            freeCompilerArgs = listOf("-Xjsr305=strict")
//            jvmTarget = jvmTargetVersion
//        }
//    }
//}
//tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
//    compilerOptions {
//        freeCompilerArgs = listOf("-Xjsr305=strict")
//    }
//}


tasks.compileJava {
    options.release.set(javaVersion)
}


val dependenciesDir = "dependencies"
val copyDependencies = tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath)
        .into(layout.buildDirectory.dir("libs/$dependenciesDir"))
}


tasks.named<Jar>("jar") {
    dependsOn(copyDependencies)

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


// Distribution zip: the shell jar (keeps its name — end users run `java -jar kzen-shell-<v>.jar`)
//  + dependencies/. Launcher scripts (kzen.bat/kzen.sh) and offline launcher-seeding are Phase 4.
tasks.register<Zip>("dist") {
    dependsOn("jar", "copyDependencies")
    archiveFileName.set("kzen-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))

    from(tasks.named("jar"))
    from(layout.buildDirectory.dir("libs/$dependenciesDir")) { into(dependenciesDir) }
}
