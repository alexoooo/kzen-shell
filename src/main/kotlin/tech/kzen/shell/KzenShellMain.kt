package tech.kzen.shell

import com.google.common.io.ByteStreams
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import tech.kzen.shell.context.KzenShellContext
import tech.kzen.shell.context.KzenShellProperties
import tech.kzen.shell.ui.DesktopUi


//---------------------------------------------------------------------------------------------------------------------
fun main(args: Array<String>) {
    val context = kzenShellInit(args)

    context.start()

    embeddedServer(
        Netty,
        port = context.properties.port,
        host = "127.0.0.1"
    ) {
        ktorMain(context)
        kzenShellStarted()
    }.start(wait = true)
}


//---------------------------------------------------------------------------------------------------------------------
fun kzenShellInit(args: Array<String>): KzenShellContext {
    val port = KzenShellProperties.readPort(args) ?: 8080
    DesktopUi.setPort(port)

    DesktopUi.show()

    val properties = KzenShellProperties(
        "../work/kzen-launcher/kzen-launcher-0.28.1/",
//        "file:///C:/Users/ostro/IdeaProjects/kzen-launcher/kzen-launcher-jvm/build/libs/kzen-launcher-0.28.0.zip",
        "https://github.com/alexoooo/kzen-launcher/releases/download/v0.28.1/kzen-launcher-0.28.1.zip",
        port
    )

    val context = KzenShellContext(properties)

    Runtime.getRuntime().addShutdownHook(Thread {
        context.close()
    })

    return context
}


fun kzenShellStarted() {
    DesktopUi.onLoaded()
}


//---------------------------------------------------------------------------------------------------------------------
fun Application.ktorMain(
    context: KzenShellContext
) {
    install(ContentNegotiation) {
        jackson()
    }

    routing {
        routeRequests(context)
    }
}


private fun Routing.routeRequests(
    context: KzenShellContext
) {
    get("/") {
        call.respondRedirect("main/index.html")
    }
    get("/index.html") {
        call.respondRedirect("main/index.html")
    }

    get("/shell/project") {
        val response = context.proxyHandler.list()
        call.respond(response)
    }
    get("/shell/project/start") {
        context.proxyHandler.start(call.parameters)
        call.respondText("started")
    }
    get("/shell/project/stop") {
        val response = context.proxyHandler.stop(call.parameters)
        call.respond(response)
    }

    get("{...}") {
        routeProxy(context)
    }
    put("{...}") {
        routeProxy(context)
    }
    post("{...}") {
        routeProxy(context)
    }
    get("{...}/") {
        routeProxy(context)
    }
}


private suspend fun PipelineContext<Unit, ApplicationCall>.routeProxy(
    context: KzenShellContext
) {
    val result = context.proxyHandler.handle(call.request)

    for (e in result.header) {
        if (HttpHeaders.isUnsafe(e.key)) {
            continue
        }
        if (call.response.headers[e.key] != e.value) {
            call.response.header(e.key, e.value)
        }
    }

    call.respondOutputStream(
        result.mimeType?.let { ContentType.parse(it) },
        result.statusCode?.let { HttpStatusCode.fromValue(it) }
    ) {
        result.data?.use {
            ByteStreams.copy(it, this)
        }
    }
}