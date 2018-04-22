package tech.kzen.shell.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component


@Component
@ConfigurationProperties("shell.main")
class ShellProperties {
    var path: String? = null
    var download: String? = null
}