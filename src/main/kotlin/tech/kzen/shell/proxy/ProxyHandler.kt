package tech.kzen.shell.proxy

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.utils.io.jvm.javaio.*
//import org.springframework.core.io.InputStreamResource
//import org.springframework.core.io.Resource
//import org.springframework.http.HttpHeaders
//import org.springframework.http.HttpMethod
//import org.springframework.stereotype.Component
//import org.springframework.web.reactive.function.server.ServerRequest
//import org.springframework.web.reactive.function.server.ServerResponse
//import org.springframework.web.reactive.function.server.body
//import reactor.core.publisher.Mono
import tech.kzen.shell.context.KzenShellProperties
import tech.kzen.shell.registry.ProcessRegistry
import tech.kzen.shell.registry.ProjectRegistry
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Paths


class ProxyHandler(
        private val projectRegistry: ProjectRegistry,
        private val processRegistry: ProcessRegistry,
        private val properties: KzenShellProperties
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val forwardHeaders = listOf(
            HttpHeaders.ContentType)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun start(parameters: Parameters) {
        val name = parameters.getParam("name")
        val location = parameters.getParam("location")

        val jvmArgs = parameters.getParamOrNull("args") ?: ""

        projectRegistry.start(name, Paths.get(location), jvmArgs)

//        return ServerResponse
//                .ok()
//                .body(Mono.just("started"))
    }


    fun stop(parameters: Parameters): Boolean {
        val projectName = parameters.getParam("name")
        return projectRegistry.stop(projectName)

//        return ServerResponse
//                .ok()
//                .body(Mono.just(stopped.toString()))
    }


    fun list(): List<String> {
        return projectRegistry.list().toList()
//        val quoted = projectRegistry.list().map { "\"$it\"" }
//        val delimited = quoted.joinToString()
//        val list = "[$delimited]"
//
//        return ServerResponse
//                .ok()
//                .body(Mono.just(list))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun handle(request: ApplicationRequest): ProxyResult {
        val excludingInitialSlash =
            request.path().substring(1)

        val endOfName = excludingInitialSlash.indexOf("/")
        if (endOfName == -1) {
            // sub-path required, direct resources not allowed
            return ProxyResult(statusCode = 404)
        }

        val encodedName = excludingInitialSlash.substring(0, endOfName)
        val name = URI(encodedName).path

        val adjustedName =
            if (name == "main") {
                // TODO: centralize this logic
                val fullPath = Paths
                    .get(properties.path!!)
                    .resolve("main.jar")
                    .toAbsolutePath()
                    .normalize()
                    .toString()

                val mainInfo = processRegistry.findByAttribute("location", fullPath)

                mainInfo.name
            }
            else {
                name
            }

        val subPath = excludingInitialSlash.substring(endOfName + 1)

        val info = processRegistry.get(adjustedName)
        val port = info.attributes["port"]

        val querySuffix =
            if (request.queryParameters.isEmpty()) {
                ""
            }
            else {
                "?" + URI(request.uri).rawQuery
            }

        val uri = URI("http://localhost:$port/$subPath$querySuffix")

        return when (request.httpMethod) {
            HttpMethod.Get ->
                proxyGet(request, uri)

            HttpMethod.Post ->
                proxyPostOrPut(request, uri, true)

            HttpMethod.Put ->
                proxyPostOrPut(request, uri, false)

            else ->
                ProxyResult(
                    statusCode = HttpStatusCode.BadRequest.value,
                    mimeType = ContentType.Text.Plain.contentType,
                    data = "Unsupported method: ${request.httpMethod}".byteInputStream())
        }
    }


    private fun proxyGet(
        request: ApplicationRequest,
        uri: URI
    ): ProxyResult {
        return try {
            val connection = uri.toURL().openConnection() as HttpURLConnection

            val acceptHeader = request.headers.getAll("Accept")?.joinToString(", ") ?: ""
//            val acceptHeader = serverRequest.headers().accept().joinToString(", ") { it.type }

            connection.setRequestProperty("Accept", acceptHeader)

            val isOk = connection.responseCode == HttpURLConnection.HTTP_OK

            val responseStream: InputStream =
                    if (isOk) {
                        connection.inputStream
                    }
                    else {
                        connection.errorStream
                    }

            val headerMap = mutableMapOf<String, String>()
            for ((key, values) in connection.headerFields) {
                if (key != null) {
                    for (value in values) {
                        headerMap[key] = value
//                            responseBuilder.header(key, value)
                    }
                }
            }

//            if (connection.contentLength == -1) {
//                val resource: Resource = InputStreamResource(responseStream)
//                responseBuilder
//                    .body(Mono.just(resource))
//            }
//            else {
//                val responseBytes = responseStream.use {
//                    ByteStreams.toByteArray(it)
//                }
//
//                if (responseBytes.isEmpty()) {
//                    responseBuilder.build()
//                }
//                else {
//                    responseBuilder.body(Mono.just(responseBytes))
//                }
//            }

            ProxyResult(
                statusCode = connection.responseCode,
                mimeType = connection.contentType,
                data = responseStream,
                header = headerMap
            )
        }
        catch (e: Exception) {
//            ServerResponse
//                    .badRequest()
//                    .body(Mono.just(e.message ?: ""))
            ProxyResult(
                statusCode = HttpStatusCode.BadRequest.value,
                mimeType = ContentType.Text.Plain.contentType,
                data = (e.message ?: "").byteInputStream())
        }
    }


    private fun headerMap(connection: HttpURLConnection): Map<String, String> {
        val headerMap = mutableMapOf<String, String>()
        for ((key, values) in connection.headerFields) {
            if (key != null) {
                for (value in values) {
                    headerMap[key] = value
//                            responseBuilder.header(key, value)
                }
            }
        }
        return headerMap
    }


    private fun proxyPostOrPut(
        request: ApplicationRequest,
        uri: URI,
        postOrPut: Boolean
    ): ProxyResult {
        return try {
            val builder = HttpRequest.newBuilder(uri)

            val body = request.receiveChannel()
            val bodyPublisher = HttpRequest.BodyPublishers.ofInputStream{ body.toInputStream() }
            if (postOrPut) {
                builder.POST(bodyPublisher)
            }
            else {
                builder.PUT(bodyPublisher)
            }

            for (forwardHeader in forwardHeaders) {
                val values = request.headers.getAll(forwardHeader)
                values?.forEach { builder.setHeader(forwardHeader, it) }
            }

            val httpRequest = builder.build()
            val client = HttpClient.newHttpClient()
            val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream())

            val headerMap = mutableMapOf<String, String>()
            for ((key, values) in response.headers().map()) {
                if (key != null) {
                    for (value in values) {
                        headerMap[key] = value
                    }
                }
            }

            val mimeType = response.headers().firstValue(HttpHeaders.ContentType).orElse(null)

            ProxyResult(
                statusCode = response.statusCode(),
                mimeType = mimeType,
                data = response.body(),
                header = headerMap
            )
        }
        catch (e: Exception) {
            ProxyResult(
                statusCode = HttpStatusCode.BadRequest.value,
                mimeType = ContentType.Text.Plain.contentType,
                data = (e.message ?: "").byteInputStream())
        }
    }



    //-----------------------------------------------------------------------------------------------------------------
    private fun Parameters.getParam(
        parameterName: String
    ): String {
        return getParam(parameterName) { it }
    }


    private fun <T> Parameters.getParam(
        parameterName: String,
        parser: (String) -> T
    ): T {
        val queryParamValues: List<String>? = getAll(parameterName)
        require(! queryParamValues.isNullOrEmpty()) { "'$parameterName' required" }
        require(queryParamValues.size == 1) { "Single '$parameterName' expected: $queryParamValues" }
        return parser(queryParamValues.single())
    }


    private fun Parameters.getParamOrNull(
        parameterName: String
    ): String? {
        val queryParamValues: List<String> = getAll(parameterName)
            ?: return null

        require(queryParamValues.isNotEmpty()) { "'$parameterName' required" }
        require(queryParamValues.size == 1) { "Single '$parameterName' expected: $queryParamValues" }

        return queryParamValues.single()
    }
}