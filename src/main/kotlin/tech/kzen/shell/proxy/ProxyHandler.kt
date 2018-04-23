package tech.kzen.shell.proxy

import com.google.common.io.ByteStreams
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import tech.kzen.shell.process.ProcessRegistry
import tech.kzen.shell.properties.ShellProperties
import java.net.URI
import java.net.URL
import java.nio.file.Paths


@Component
class ProxyHandler(
        private val processRegistry: ProcessRegistry,
        private val properties: ShellProperties
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun handle(serverRequest: ServerRequest): Mono<ServerResponse> {
        val excludingInitialSlash =
                serverRequest.path().substring(1)

        val endOfName = excludingInitialSlash.indexOf("/")
        if (endOfName == -1 || excludingInitialSlash == "index.html") {
            return ServerResponse
                    .permanentRedirect(URI("main/index.html"))
                    .build()
        }

        val name = excludingInitialSlash.substring(0, endOfName)
        val adjustedName =
                if (name == "main") {
                    // TODO: centralize this logic
                    val fullPath =
                            Paths.get(properties.path!!).toAbsolutePath().toString()

                    val mainInfo =
                            processRegistry.findByAttribute("location", fullPath)

                    mainInfo.name
                }
                else {
                    name
                }

        val subPath = excludingInitialSlash.substring(endOfName + 1)

        val info = processRegistry.get(adjustedName)
        val port = info.attributes["port"]

        val url = URL("http://localhost:$port/$subPath")

        // TODO: reactive download
        val downloadBytes = url
                .openStream()
                .use { ByteStreams.toByteArray(it) }

        return ServerResponse
                .ok()
                .body(Mono.just(downloadBytes))
    }
}