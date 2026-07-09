import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


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


// Build stamp: version + build timestamp baked into the jar at /kzen-shell-build.properties, loaded at
// startup by BuildInfo and logged so the running shell binary is identifiable (no UI to hover).
// Deliberately never up-to-date so every build re-stamps the moment of build.
val buildInfoDir = layout.buildDirectory.dir("generated-resources")
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val buildInfoFile = buildInfoDir.map { it.file("kzen-shell-build.properties") }
    val buildVersion = version.toString()
    outputs.file(buildInfoFile)
    outputs.upToDateWhen { false }
    doLast {
        val timestamp = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        buildInfoFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("version=$buildVersion\ntimestamp=$timestamp\n")
        }
    }
}

sourceSets.main {
    resources.srcDir(buildInfoDir)
}

tasks.withType<ProcessResources> {
    dependsOn(generateBuildInfo)
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
