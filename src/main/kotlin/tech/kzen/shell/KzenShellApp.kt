package tech.kzen.shell

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
class KzenShellApp


fun main(args: Array<String>) {
    runApplication<KzenShellApp>(*args)
}
