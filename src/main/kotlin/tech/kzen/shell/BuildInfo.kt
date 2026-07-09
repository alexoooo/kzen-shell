package tech.kzen.shell

import java.util.Properties


// Version + build timestamp baked into the artifact by the generateBuildInfo Gradle task (a classpath
//  resource, so it travels inside the jar). Loaded at startup and logged, so the running shell binary
//  is identifiable (the shell is a headless reverse proxy with no UI logo to hover). Intentionally
//  duplicated from kzen-auto / kzen-launcher — the shell shares no module with them.
data class BuildInfo(
    val version: String,
    val timestamp: String?
) {
    fun display(): String {
        return if (timestamp != null) {
            "$version (built $timestamp)"
        }
        else {
            version
        }
    }


    companion object {
        fun load(resource: String): BuildInfo? {
            val stream = BuildInfo::class.java.getResourceAsStream(resource)
                ?: return null

            val properties = Properties()
            stream.use {
                properties.load(it)
            }

            val version = properties.getProperty("version")
                ?: return null

            return BuildInfo(version, properties.getProperty("timestamp"))
        }
    }
}
