package tech.kzen.shell

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import tech.kzen.shell.context.KzenShellContext
import tech.kzen.shell.context.KzenShellProperties
import tech.kzen.shell.security.SecurityGate
import tech.kzen.shell.ui.DesktopUi


//---------------------------------------------------------------------------------------------------------------------
private val logger = LoggerFactory.getLogger("tech.kzen.shell.KzenShellMain")


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
    // Identify the running shell binary in the log (headless proxy — no logo to hover, unlike the
    //  launcher/project UIs which show their build on logo hover).
    val buildInfo = BuildInfo.load("/kzen-shell-build.properties")
    logger.info("kzen-shell {}", buildInfo?.display() ?: "(build stamp unavailable)")

    // Launcher + project-archetype sources resolve via --args > kzen-shell.properties > GitHub default.
    val properties = KzenShellProperties.load(args)

    DesktopUi.setPort(properties.port)
    DesktopUi.show()

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
    install(IgnoreTrailingSlash)
    install(ContentNegotiation) {
        // SER5: kotlinx.serialization (Jackson removed). Serves the two control endpoints' JSON —
        // List<RunningProjectStatus> and a Boolean (both @Serializable / built-in). Proxied traffic streams
        // through untouched and never hits this converter.
        json()
    }

    SecurityGate.install(this)

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
        try {
            context.proxyHandler.start(call.parameters)
            call.respondText("started")
        }
        catch (e: IllegalArgumentException) {
            call.respondText(e.message ?: "invalid request", status = HttpStatusCode.BadRequest)
        }
    }
    get("/shell/project/stop") {
        try {
            val response = context.proxyHandler.stop(call.parameters)
            call.respond(response)
        }
        catch (e: IllegalArgumentException) {
            call.respondText(e.message ?: "invalid request", status = HttpStatusCode.BadRequest)
        }
    }

    route("{...}") {
        handle {
            context.proxyHandler.handle(call)
        }
    }
}