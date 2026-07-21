package tech.kzen.shell.proxy

import io.ktor.client.*
import io.ktor.client.network.sockets.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import tech.kzen.shell.context.KzenShellProperties
import tech.kzen.shell.model.RunningProjectStatus
import tech.kzen.shell.registry.ProcessRegistry
import tech.kzen.shell.registry.ProjectRegistry
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths


class ProxyHandler(
        private val projectRegistry: ProjectRegistry,
        private val processRegistry: ProcessRegistry,
        private val properties: KzenShellProperties,
        private val httpClient: HttpClient
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ProxyHandler::class.java)

        // Hop-by-hop headers (RFC 7230 §6.1) plus Host/Content-Length, matched case-insensitively.
        // Content-Type/Content-Length/Transfer-Encoding are handled via the request body (OutgoingContent)
        //  and the response framing, never copied as plain headers.
        private val hopByHopHeaders: Set<String> = setOf(
            HttpHeaders.Connection,
            HttpHeaders.TransferEncoding,
            HttpHeaders.Upgrade,
            "Keep-Alive",
            "Proxy-Connection",
            HttpHeaders.Host,
            HttpHeaders.ContentLength
        ).mapTo(HashSet()) { it.lowercase() }


        private fun isHopByHop(name: String): Boolean {
            val lower = name.lowercase()
            return lower in hopByHopHeaders || lower.startsWith("proxy-")
        }


        // Request direction: also drop Content-Type (carried by the OutgoingContent body).
        private fun forwardRequestHeader(name: String): Boolean =
            !isHopByHop(name) && !name.equals(HttpHeaders.ContentType, ignoreCase = true)


        // Response direction: also drop Ktor's engine-managed "unsafe" set (Content-Type/Length/TE).
        private fun forwardResponseHeader(name: String): Boolean =
            !isHopByHop(name) && !HttpHeaders.isUnsafe(name)


        private fun isConnectivityFailure(error: Throwable): Boolean {
            var cursor: Throwable? = error
            while (cursor != null) {
                if (cursor is ConnectException ||
                        cursor is SocketException ||
                        cursor is ConnectTimeoutException) {
                    return true
                }
                cursor = cursor.cause
            }
            return false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun start(parameters: Parameters) {
        val name = parameters.getParam("name")
        val location = parameters.getParam("location")

        val jvmArgs = parameters.getParamOrNull("args") ?: ""

        // Defense-in-depth (SecurityGate blocks cross-site callers): the browser supplies the
        //  path, so only spawn a project layout — main.jar inside the given home — never an
        //  arbitrary file.
        val home = Paths.get(location).toAbsolutePath().normalize()
        require(Files.isRegularFile(home.resolve("main.jar"))) { "main.jar not found in: $home" }

        logger.info("Project start requested: {} @ {}", name, home)
        projectRegistry.start(name, home, jvmArgs)
    }


    fun stop(parameters: Parameters): Boolean {
        val projectName = parameters.getParam("name")
        return projectRegistry.stop(projectName)
    }


    fun list(): List<RunningProjectStatus> {
        return projectRegistry.list()
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun handle(call: ApplicationCall) {
        val excludingInitialSlash =
            call.request.path().substring(1)

        val endOfName = excludingInitialSlash.indexOf("/")
        if (endOfName == -1) {
            // sub-path required, direct resources not allowed
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val encodedName = excludingInitialSlash.substring(0, endOfName)
        val name = URI(encodedName).path

        val info: ProcessRegistry.Info
        if (name == "main") {
            // TODO: centralize this logic
            val fullPath = Paths
                .get(properties.path)
                .resolve("main.jar")
                .toAbsolutePath()
                .normalize()
                .toString()

            val mainInfo = processRegistry.findByAttribute("location", fullPath)
            if (mainInfo == null) {
                logger.warn("'main' alias unresolved (launcher not registered) for {}", fullPath)
                respondProxyError(call, HttpStatusCode.ServiceUnavailable, "process-unavailable", "main")
                return
            }
            info = mainInfo
        }
        else {
            val namedInfo = processRegistry.getOrNull(name)
            if (namedInfo == null) {
                logger.warn("Proxy target not registered: {}", name)
                call.respond(HttpStatusCode.NotFound)
                return
            }
            info = namedInfo
        }

        val port = info.attributes["port"]
        val subPath = excludingInitialSlash.substring(endOfName + 1)

        val querySuffix =
            if (call.request.queryParameters.isEmpty()) {
                ""
            }
            else {
                "?" + URI(call.request.uri).rawQuery
            }

        val targetUrl = "http://localhost:$port/$subPath$querySuffix"

        val requestContentLength = call.request.contentLength()
        val hasBody =
            (requestContentLength != null && requestContentLength > 0) ||
            call.request.headers[HttpHeaders.TransferEncoding] != null

        try {
            httpClient
                .prepareRequest(targetUrl) {
                    method = call.request.httpMethod
                    forwardRequestHeaders(call, this)
                    if (hasBody) {
                        setBody(ForwardedRequestBody(
                            call.receiveChannel(),
                            call.request.contentType(),
                            requestContentLength))
                    }
                }
                .execute { upstream ->
                    forwardResponseHeaders(upstream, call)

                    try {
                        call.respondBytesWriter(
                            contentType = upstream.contentType(),
                            status = upstream.status,
                            contentLength = upstream.contentLength()
                        ) {
                            upstream.bodyAsChannel().copyTo(this)
                        }
                    }
                    catch (e: IOException) {
                        // Status + headers are already committed; we cannot switch to an error page.
                        //  Abort the stream and log — nothing else can be done.
                        logger.warn("Proxy stream to '{}' interrupted after response was committed", info.name, e)
                    }
                }
        }
        catch (e: IOException) {
            // Failure before the response was committed (connect, request send, or reading status/headers).
            if (isConnectivityFailure(e)) {
                logger.warn("Upstream '{}' unavailable", info.name, e)
                respondProxyError(call, HttpStatusCode.ServiceUnavailable, "process-unavailable", info.name)
            }
            else {
                logger.warn("Proxy failure to '{}'", info.name, e)
                respondProxyError(call, HttpStatusCode.BadGateway, "proxy-failure", info.name)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun forwardRequestHeaders(call: ApplicationCall, builder: HttpRequestBuilder) {
        builder.headers {
            for ((headerName, values) in call.request.headers.entries()) {
                if (!forwardRequestHeader(headerName)) {
                    continue
                }
                for (value in values) {
                    append(headerName, value)
                }
            }
        }
    }


    private fun forwardResponseHeaders(upstream: HttpResponse, call: ApplicationCall) {
        for ((headerName, values) in upstream.headers.entries()) {
            if (!forwardResponseHeader(headerName)) {
                continue
            }
            for (value in values) {
                call.response.headers.append(headerName, value, safeOnly = false)
            }
        }
    }


    private suspend fun respondProxyError(
        call: ApplicationCall,
        status: HttpStatusCode,
        error: String,
        name: String
    ) {
        val body = buildJsonObject {
            put("error", error)
            put("name", name)
        }.toString()
        call.respondText(body, ContentType.Application.Json, status)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class ForwardedRequestBody(
        private val channel: ByteReadChannel,
        override val contentType: ContentType?,
        override val contentLength: Long?
    ) : OutgoingContent.ReadChannelContent() {
        override fun readFrom(): ByteReadChannel = channel
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
        require(!queryParamValues.isNullOrEmpty()) { "'$parameterName' required" }
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
