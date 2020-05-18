import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack


plugins {
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    id("org.springframework.boot") version springBootVersion
    id("io.spring.dependency-management") version dependencyManagementVersion
}


group = "tech.kzen"
version = "0.14.0"


repositories {
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    mavenCentral()
}


dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

//    compile('org.springframework.boot:spring-boot-starter-web')

    implementation("com.google.guava:guava:$guavaVersion")
    implementation("io.github.microutils:kotlin-logging:$kotlinLogging")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}


tasks {
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = jvmTargetVersion
        }
    }
//    compileTestKotlin {
//        kotlinOptions.jvmTarget = jvmTargetVersion
//    }
}


tasks.getByName<BootJar>("bootJar") {
    archiveClassifier.set("boot")
}