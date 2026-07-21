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
import tech.kzen.shell.util.FreePortUtil
import java.net.BindException


//---------------------------------------------------------------------------------------------------------------------
private val logger = LoggerFactory.getLogger("tech.kzen.shell.KzenShellMain")


fun main(args: Array<String>) {
    val context = kzenShellInit(args)
        ?: return

    context.start()

    try {
        embeddedServer(
            Netty,
            port = context.properties.port,
            host = "127.0.0.1"
        ) {
            ktorMain(context)
            kzenShellStarted()
        }.start(wait = true)
    }
    catch (e: Exception) {
        if (!isBindFailure(e)) {
            throw e
        }

        // Backstop for the window between the pre-flight probe and the engine binding: reap the launcher
        //  child this instance already spawned, then repaint over whatever the module hook put up.
        logger.error("Unable to bind port {} — is Kzen already running?", context.properties.port)
        context.close()
        DesktopUi.showBindFailure(context.properties.port)
    }
}


private fun isBindFailure(error: Throwable): Boolean {
    var cursor: Throwable? = error
    while (cursor != null) {
        if (cursor is BindException) {
            return true
        }
        cursor = cursor.cause
    }
    return false
}


//---------------------------------------------------------------------------------------------------------------------
// Null when the shell cannot run at all, having already told the user why.
fun kzenShellInit(args: Array<String>): KzenShellContext? {
    // Identify the running shell binary in the log (headless proxy — no logo to hover, unlike the
    //  launcher/project UIs which show their build on logo hover).
    val buildInfo = BuildInfo.load("/kzen-shell-build.properties")
    logger.info("kzen-shell {}", buildInfo?.display() ?: "(build stamp unavailable)")

    // Launcher + project-archetype sources resolve via --args > kzen-shell.properties > GitHub default.
    val properties = KzenShellProperties.load(args)

    DesktopUi.setPort(properties.port)
    DesktopUi.show()

    // Probed before the context exists so a second instance never downloads or spawns a launcher child of
    //  its own. Ktor runs the application module before the engine binds, so without this the UI would
    //  flip to "Ready" and open a browser onto the FIRST instance before the bind ever fails.
    if (!FreePortUtil.isTcpPortFree(properties.port)) {
        logger.error("Port {} already in use — is Kzen already running?", properties.port)
        DesktopUi.showBindFailure(properties.port)
        return null
    }

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