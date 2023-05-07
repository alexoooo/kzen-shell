package tech.kzen.shell

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
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
        "../work/kzen-launcher/kzen-launcher-jvm-0.25.1/",
        "file:///C:/Users/ao/IdeaProjects/kzen-launcher/kzen-launcher-jvm/build/libs/kzen-launcher-jvm-0.25.1-SNAPSHOT.zip",
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
        println("list")
    }
    get("/shell/project/start") {
        println("start")
    }
    get("/shell/project/stop") {
        println("stop")
    }

    get("{...}") {
        println("get " + call.request.path())
    }
    put("{...}") {
        println("put " + call.request.path())
    }
    post("{...}") {
        println("post " + call.request.path())
    }
}