package tech.kzen.shell.security

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals


// Proves the interceptor wiring: the gate runs ahead of routing (403 before any handler),
//  and clean requests pass through untouched. The decision matrix itself is SecurityGateTest.
class SecurityGateKtorTest {
    @Test
    fun `cross-site request rejected before routing, clean request passes`() = testApplication {
        application {
            SecurityGate.install(this)
            routing {
                get("/shell/project/stop") {
                    call.respondText("stopped")
                }
            }
        }

        val denied = client.get("/shell/project/stop?name=x") {
            header("Sec-Fetch-Site", "cross-site")
        }
        assertEquals(HttpStatusCode.Forbidden, denied.status)

        val rebound = client.get("/shell/project/stop?name=x") {
            header(HttpHeaders.Host, "evil.test:8080")
        }
        assertEquals(HttpStatusCode.Forbidden, rebound.status)

        val allowed = client.get("/shell/project/stop?name=x")
        assertEquals(HttpStatusCode.OK, allowed.status)
        assertEquals("stopped", allowed.bodyAsText())
    }
}
