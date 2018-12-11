package tech.kzen.shell

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.reactive.config.EnableWebFlux
import tech.kzen.shell.ui.DesktopUi


@SpringBootApplication
@EnableWebFlux
class KzenShellApp


fun main(args: Array<String>) {
    DesktopUi.show()
    runApplication<KzenShellApp>(*args)
}
