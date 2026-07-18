import dist.ProvisionAdoptiumJdk
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


plugins {
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
}


group = "tech.kzen"
version = "0.30.0-SNAPSHOT"


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

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    implementation("com.google.guava:guava:$guavaVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
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
    archiveFileName.set("kzen-$version.jar")

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


// Release config bundled into the dist zip: a kzen-shell.properties pointing at the launcher's GitHub
//  release. The version is derived from `version` with `-SNAPSHOT` stripped, so even a dev-built dist
//  names the eventual release (the git-tracked repo-root kzen-shell.properties stays the dev config —
//  the shell reads CWD first, so this bundled copy only matters once the zip is unzipped elsewhere).
val generateReleaseConfig = tasks.register("generateReleaseConfig") {
    val releaseVersion = version.toString().removeSuffix("-SNAPSHOT")
    val outputFile = layout.buildDirectory.file("dist-config/kzen-shell.properties")
    inputs.property("releaseVersion", releaseVersion)
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                "launcher.zip=https://github.com/alexoooo/kzen-launcher/releases/download/" +
                    "v$releaseVersion/kzen-launcher-$releaseVersion.zip\n" +
                "launcher.dir=work/kzen-launcher/kzen-launcher-$releaseVersion\n")
        }
    }
}


// Adoptium Temurin JDK bundled into the Windows distribution so end users need no local Java install.
//  Its feature version tracks the compile target so the shipped runtime matches what the app is built for.
val provisionJdk = tasks.register<ProvisionAdoptiumJdk>("provisionJdk") {
    featureVersion.set(javaVersion)
    operatingSystem.set("windows")
    architecture.set("x64")
    downloadCacheDirectory.set(gradle.gradleUserHomeDir.resolve("caches/kzen-adoptium-jdk"))
    jdkDirectory.set(layout.buildDirectory.dir("jdk"))
}


// Windows launchers: kzen.bat (javaw + start) runs windowless; kzen-cmd.bat (java) keeps a console for logs.
//  Everything the action needs is captured into task-local vals so the action stays config-cache-safe.
val generateWindowsLaunchers = tasks.register("generateWindowsLaunchers") {
    val applicationJar = "kzen-$version.jar"
    val bundledJdkDir = "jdk"
    val launcherJvmArgs = "-XX:+UseShenandoahGC -Xmx64m"
    val outputDir = layout.buildDirectory.dir("launchers")
    inputs.property("applicationJar", applicationJar)
    inputs.property("launcherJvmArgs", launcherJvmArgs)
    inputs.property("bundledJdkDir", bundledJdkDir)
    outputs.dir(outputDir)
    doLast {
        val lineSeparator = "\r\n"
        fun batchScript(command: String) =
            listOf("@echo off", "cd /d \"%~dp0\"", command)
                .joinToString(lineSeparator, postfix = lineSeparator)
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("kzen.bat").writeText(
            batchScript("start \"\" \"$bundledJdkDir\\bin\\javaw.exe\" $launcherJvmArgs -jar \"$applicationJar\""))
        dir.resolve("kzen-cmd.bat").writeText(
            batchScript("\"$bundledJdkDir\\bin\\java.exe\" $launcherJvmArgs -jar \"$applicationJar\""))
    }
}


// Payload shared by both archives: the shell jar (named kzen-<v>.jar; its Class-Path manifest resolves
//  dependencies/ as a sibling), the dependency jars, and the bundled release config.
val distributionRoot = "kzen-$version"
val distributionContent = copySpec {
    from(tasks.named("jar"))
    from(copyDependencies) { into(dependenciesDir) }
    from(generateReleaseConfig)
}

val configureAppArchive: (Zip) -> Unit = { archive ->
    archive.destinationDirectory.set(layout.buildDirectory.dir("dist"))
    archive.into(distributionRoot) { with(distributionContent) }
}


// kzen-<v>-jars.zip: the JVM app for users who bring their own JDK.
tasks.register<Zip>("distJars") {
    configureAppArchive(this)
    archiveFileName.set("kzen-$version-jars.zip")
}


// kzen-<v>.zip: the turnkey Windows app — the jars plus the bundled JDK and launchers.
tasks.register<Zip>("distWindows") {
    configureAppArchive(this)
    archiveFileName.set("kzen-$version.zip")
    into(distributionRoot) {
        from(provisionJdk.flatMap { it.jdkDirectory })
        from(generateWindowsLaunchers)
    }
}
