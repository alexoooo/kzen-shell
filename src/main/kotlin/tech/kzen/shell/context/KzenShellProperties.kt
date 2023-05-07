package tech.kzen.shell.context

//import org.springframework.boot.context.properties.ConfigurationProperties
//import org.springframework.stereotype.Component


//@Component
//@ConfigurationProperties("shell.main")
data class KzenShellProperties(
    val path: String? = null,
    val download: String? = null,
    val port: Int = 80
) {
    companion object {
        private const val serverPortPrefix = "--server.port="
        private val serverPortRegex = Regex(
            Regex.escape(serverPortPrefix) + "\\d+")

        fun readPort(args: Array<String>): Int? {
            val match = args
                .lastOrNull { it.matches(serverPortRegex) }
                ?: return null

            val portText = match.substring(serverPortPrefix.length)
            return portText.toInt()
        }
    }
}