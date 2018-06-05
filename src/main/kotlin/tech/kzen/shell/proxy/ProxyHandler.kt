package tech.kzen.shell.proxy

import com.google.common.io.ByteStreams
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import tech.kzen.shell.registry.ProcessRegistry
import tech.kzen.shell.properties.ShellProperties
import tech.kzen.shell.registry.ProjectRegistry
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.file.Paths


@Component
class ProxyHandler(
        private val projectRegistry: ProjectRegistry,
        private val processRegistry: ProcessRegistry,
        private val properties: ShellProperties
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun start(serverRequest: ServerRequest): Mono<ServerResponse> {
        val name = serverRequest.queryParam("name")
                .orElseThrow { IllegalArgumentException("project name required") }

        val location = serverRequest.queryParam("location")
                .orElseThrow { IllegalArgumentException("project location required") }

        projectRegistry.start(name, Paths.get(location))

        return ServerResponse
                .ok()
                .body(Mono.just("started"))
    }


    fun stop(serverRequest: ServerRequest): Mono<ServerResponse> {
        val name = serverRequest.queryParam("name")
                .orElseThrow { IllegalArgumentException("project name required") }

        val stopped = projectRegistry.stop(name)

        return ServerResponse
                .ok()
                .body(Mono.just(stopped.toString()))
    }


    fun list(serverRequest: ServerRequest): Mono<ServerResponse> {
        val quoted = projectRegistry.list().map { "\"$it\"" }
        val delimited = quoted.joinToString()
        val list = "[$delimited]"

        return ServerResponse
                .ok()
                .body(Mono.just(list))
    }


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


        val querySuffix =
                if (serverRequest.queryParams().isEmpty()) {
                    ""
                }
                else {
                    "?" + serverRequest.uri().rawQuery
                }

        val url = URL("http://localhost:$port/$subPath$querySuffix")

        // TODO: reactive download
        var urlStream: InputStream? = null

        return try {
            urlStream = url.openStream()
            val downloadBytes = ByteStreams.toByteArray(urlStream)
            ServerResponse
                    .ok()
                    .body(Mono.just(downloadBytes))
        }
        catch (e: IllegalStateException) {
            ServerResponse
                    .badRequest()
                    .body(Mono.just(e.message ?: ""))
        }
        catch (e: IOException) {
            ServerResponse
                    .notFound()
                    .build()
        }
        finally {
            if (urlStream != null) {
                urlStream.close()
            }
        }
    }
}