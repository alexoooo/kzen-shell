package tech.kzen.shell

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
//        "../work/kzen-launcher/kzen-launcher-0.28.1/",
        "../work/kzen-launcher/kzen-launcher-0.29.1-SNAPSHOT/",
        "file:///C:/Users/ostro/IdeaProjects/kzen-launcher/kzen-launcher-jvm/build/libs/kzen-launcher-0.29.1-SNAPSHOT.zip",
//        "https://github.com/alexoooo/kzen-launcher/releases/download/v0.28.1/kzen-launcher-0.28.1.zip",
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
    install(IgnoreTrailingSlash)
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

    route("{...}") {
        handle {
            context.proxyHandler.handle(call)
        }
    }
}