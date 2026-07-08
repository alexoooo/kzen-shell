package tech.kzen.shell.context

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties


data class KzenShellProperties(
    val path: String? = null,
    val download: String? = null,
    val port: Int = 80
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(KzenShellProperties::class.java)

        private const val propertiesFileName = "kzen-shell.properties"

        // Properties-file keys and their equivalent --arg overrides.
        private const val launcherDirKey = "launcher.dir"
        private const val launcherZipKey = "launcher.zip"

        private const val launcherDirArg = "--launcher.dir="
        private const val launcherZipArg = "--launcher.zip="

        private const val serverPortPrefix = "--server.port="
        private val serverPortRegex = Regex(
            Regex.escape(serverPortPrefix) + "\\d+")


        //-------------------------------------------------------------------------------------------------------------
        // The launcher source comes from kzen-shell.properties (dev: a relative file path, resolved to a
        //  file:// URI so the checked-in config has no machine strings; release: a full https URL),
        //  overridable by the matching --arg. The version lives only in these paths, never in source.
        //  (The project archetype is the launcher's concern — configured in kzen-launcher, not here.)
        fun load(args: Array<String>): KzenShellProperties {
            val properties = readPropertiesFile()

            val launcherDir = argValue(args, launcherDirArg) ?: properties.getProperty(launcherDirKey)
            val launcherZip = argValue(args, launcherZipArg) ?: properties.getProperty(launcherZipKey)

            return KzenShellProperties(
                path = launcherDir,
                download = launcherZip?.let { asUri(it) },
                port = readPort(args) ?: 8080)
        }


        fun readPort(args: Array<String>): Int? {
            val match = args
                .lastOrNull { it.matches(serverPortRegex) }
                ?: return null

            return match.substring(serverPortPrefix.length).toInt()
        }


        //-------------------------------------------------------------------------------------------------------------
        private fun argValue(args: Array<String>, prefix: String): String? {
            return args
                .lastOrNull { it.startsWith(prefix) }
                ?.substring(prefix.length)
        }


        private fun readPropertiesFile(): Properties {
            val properties = Properties()

            val file = Paths.get(propertiesFileName)
            if (Files.isRegularFile(file)) {
                logger.info("Reading shell config: {}", file.toAbsolutePath().normalize())
                Files.newInputStream(file).use { properties.load(it) }
            }

            return properties
        }


        // A filesystem path (no URL scheme) is resolved to an absolute file:// URI; an already-schemed
        //  value (http/https/file) is passed through unchanged.
        private fun asUri(value: String): String {
            val lower = value.lowercase()
            if (lower.startsWith("http://") ||
                    lower.startsWith("https://") ||
                    lower.startsWith("file:")) {
                return value
            }
            return Paths.get(value).toAbsolutePath().normalize().toUri().toString()
        }
    }
}
