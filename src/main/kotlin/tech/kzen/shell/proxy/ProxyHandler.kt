package tech.kzen.shell.proxy

import com.google.common.io.ByteStreams
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import tech.kzen.shell.registry.ProcessRegistry
import tech.kzen.shell.properties.ShellProperties
import tech.kzen.shell.registry.ProjectRegistry
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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

        val encodedName = excludingInitialSlash.substring(0, endOfName)
        val name = URI(encodedName).path

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

        val uri = URI("http://localhost:$port/$subPath$querySuffix")

        // TODO: reactive download
        return when (serverRequest.method()) {
            HttpMethod.GET ->
                proxyGet(serverRequest, uri)

            HttpMethod.POST ->
                proxyPost(serverRequest, uri)

            else ->
                ServerResponse
                        .badRequest()
                        .body(Mono.just("Unsupported method: ${serverRequest.methodName()}"))
        }
    }


    private fun proxyGet(
            serverRequest: ServerRequest,
            uri: URI
    ): Mono<ServerResponse> {
        return try {
            val connection = uri.toURL().openConnection() as HttpURLConnection

            val acceptHeader = serverRequest.headers().accept().joinToString(", ") { it.type }

            connection.setRequestProperty("Accept", acceptHeader)

            val isOk = connection.responseCode == HttpURLConnection.HTTP_OK

            val responseStream =
                    if (isOk) {
                        connection.inputStream
                    }
                    else {
                        connection.errorStream
                    }

            val responseBytes = responseStream.use {
                ByteStreams.toByteArray(it)
            }

            val responseBuilder = ServerResponse.status(connection.responseCode)

            if (responseBytes.isEmpty()) {
                responseBuilder.build()
            }
            else {
                responseBuilder.body(Mono.just(responseBytes))
            }
        }
        catch (e: Exception) {
            ServerResponse
                    .badRequest()
                    .body(Mono.just(e.message ?: ""))
        }
    }


    private fun proxyPost(
            serverRequest: ServerRequest,
            uri: URI
    ): Mono<ServerResponse> {
        return try {
            val contents = serverRequest.bodyToMono(ByteArray::class.java)
            contents.flatMap { body ->
                val client = HttpClient.newHttpClient()

                val request = HttpRequest.newBuilder(uri)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())

                val responseBuilder = ServerResponse.status(response.statusCode())

                val responseBytes = response.body()
                if (responseBytes.isEmpty()) {
                    responseBuilder.build()
                }
                else {
                    responseBuilder.body(Mono.just(responseBytes))
                }
            }
        }
        catch (e: Exception) {
            ServerResponse
                    .badRequest()
                    .body(Mono.just(e.message ?: ""))
        }
    }
}