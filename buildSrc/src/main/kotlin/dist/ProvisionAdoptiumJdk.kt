package dist

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject


/**
 * Downloads an Eclipse Temurin JDK from the Adoptium API, verifies its published SHA-256, and lays it
 * out under [jdkDirectory] as a fixed `jdk/` subfolder so a bundling archive references a stable path
 * (the upstream archive roots at a version-specific `jdk-<semver>/` that would change every release).
 */
abstract class ProvisionAdoptiumJdk : DefaultTask() {
    companion object {
        private const val adoptiumAssetsApi = "https://api.adoptium.net/v3/assets/latest"
        private const val vendor = "eclipse"
        private const val jvmImplementation = "hotspot"
        private const val imageType = "jdk"

        private const val normalizedJdkFolder = "jdk"
        private const val jdkReleaseMarker = "release"

        private const val checksumAlgorithm = "SHA-256"
        private const val checksumBufferBytes = 1 shl 16
        private const val partialSuffix = ".part"
    }


    @get:Input
    abstract val featureVersion: Property<Int>

    @get:Input
    abstract val operatingSystem: Property<String>

    @get:Input
    abstract val architecture: Property<String>

    @get:Internal
    abstract val downloadCacheDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val jdkDirectory: DirectoryProperty

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations


    @TaskAction
    fun provision() {
        val binary = resolveLatestBinary()
        val archive = downloadIfChecksumAbsent(binary)
        extractNormalized(archive)
    }


    private fun resolveLatestBinary(): AdoptiumBinary {
        val feature = featureVersion.get()
        val os = operatingSystem.get()
        val arch = architecture.get()
        val metadataUrl = "$adoptiumAssetsApi/$feature/$jvmImplementation" +
            "?os=$os&architecture=$arch&image_type=$imageType&vendor=$vendor"

        val response = httpClient().send(
            HttpRequest.newBuilder(URI.create(metadataUrl))
                .header("accept", "application/json")
                .build(),
            HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Adoptium metadata request failed (${response.statusCode()}): $metadataUrl"
        }

        val assets = JsonSlurper().parseText(response.body()) as List<*>
        val asset = assets.firstOrNull()
            ?: error("No Temurin $feature $imageType found for $os/$arch")
        val packaged = (asset.asMap()["binary"].asMap())["package"].asMap()

        return AdoptiumBinary(
            releaseName = asset.asMap()["release_name"] as String,
            fileName = packaged["name"] as String,
            downloadUrl = packaged["link"] as String,
            sha256 = packaged["checksum"] as String)
    }


    private fun downloadIfChecksumAbsent(binary: AdoptiumBinary): Path {
        val cacheDir = downloadCacheDirectory.get().asFile.toPath()
        Files.createDirectories(cacheDir)
        val target = cacheDir.resolve(binary.fileName)

        if (Files.exists(target) && sha256(target) == binary.sha256) {
            logger.lifecycle("Using cached Temurin ${binary.releaseName}: {}", target)
            return target
        }

        logger.lifecycle("Downloading Temurin ${binary.releaseName} from {}", binary.downloadUrl)
        val partial = cacheDir.resolve(binary.fileName + partialSuffix)
        val response = httpClient().send(
            HttpRequest.newBuilder(URI.create(binary.downloadUrl)).build(),
            HttpResponse.BodyHandlers.ofFile(partial))
        check(response.statusCode() == 200) {
            "Adoptium download failed (${response.statusCode()}): ${binary.downloadUrl}"
        }

        val actual = sha256(partial)
        check(actual == binary.sha256) {
            "Checksum mismatch for ${binary.fileName}: expected ${binary.sha256}, got $actual"
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }


    private fun extractNormalized(archive: Path) {
        fileSystemOperations.sync {
            from(archiveOperations.zipTree(archive)) {
                // Strip the archive's version-specific `jdk-<semver>/` root, re-root everything under `jdk/`.
                eachFile {
                    relativePath = RelativePath(
                        true, normalizedJdkFolder, *relativePath.segments.drop(1).toTypedArray())
                }
                includeEmptyDirs = false
            }
            into(jdkDirectory)
        }

        val releaseFile = jdkDirectory.get().asFile.toPath().resolve("$normalizedJdkFolder/$jdkReleaseMarker")
        check(Files.isRegularFile(releaseFile)) {
            "Extracted JDK missing $jdkReleaseMarker at $releaseFile — unexpected Adoptium archive layout"
        }
    }


    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance(checksumAlgorithm)
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(checksumBufferBytes)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }


    private fun httpClient(): HttpClient =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()


    private fun Any?.asMap(): Map<*, *> =
        this as? Map<*, *>
            ?: error("Unexpected Adoptium response shape")


    private data class AdoptiumBinary(
        val releaseName: String,
        val fileName: String,
        val downloadUrl: String,
        val sha256: String)
}
