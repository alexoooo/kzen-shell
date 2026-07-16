package tech.kzen.shell.context

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import tech.kzen.shell.util.FreePortUtil
import kotlin.test.Test
import kotlin.test.assertEquals


// Pins the proxy client's timeout configuration against a silent, dev-invisible regression.
//
// The proxy relays long-lived responses: kzen-auto's /logic/events SSE stream (open for the life of a page)
// and large downloads. Ktor's CIO engine defaults requestTimeout to 15s and applies it as a wall-clock cap on
// the WHOLE call context -- when it fires it cancels the response body channel mid-stream. CIO exempts SSE and
// upgrade requests, but every exemption keys off the request BODY type (SSEClientContent / ClientUpgradeContent)
// or an explicit timeout capability, and ProxyHandler forwards via a plain prepareRequest -- so none apply and
// the default silently bites.
//
// It bites INVISIBLY: the proxy has already committed status + headers by then, so it can only log, and the
// browser sees a truncated 200 rather than an error. It is also invisible in the dev loop, where the browser
// talks to kzen-auto directly and never traverses this client. A test is the only thing that would catch its
// removal, hence the ~18s runtime -- deliberate, and the shortest that proves the point (>15s).
class ProxyHttpClientTimeoutTest {
    private companion object {
        // Must exceed CIO's 15s requestTimeout default, with enough margin that timing jitter can't make a
        // regression look like a pass.
        const val streamGapMillis = 18_000L
    }


    @Test
    fun `proxy client relays a response that outlives CIO's 15s request timeout default`() {
        val port = FreePortUtil.findAvailableTcpPort()

        // A child that behaves like an idle SSE stream: responds at once, then goes quiet past the 15s
        // deadline before delivering the rest.
        val slowServer = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            routing {
                get("/slow") {
                    call.respondBytesWriter(contentType = io.ktor.http.ContentType.Text.EventStream) {
                        writeStringUtf8("first\n")
                        flush()
                        delay(streamGapMillis)
                        writeStringUtf8("second\n")
                        flush()
                    }
                }
            }
        }
        slowServer.start(wait = false)

        // The REAL configured client, not a hand-rolled copy -- deleting the HttpTimeout install from
        // KzenShellContext must fail this test. Construction is side-effect free (start() is what spawns).
        val context = KzenShellContext(
            KzenShellProperties(path = "unused", download = "http://localhost/unused", port = port))

        try {
            val body = runBlocking {
                context.httpClient.get("http://127.0.0.1:$port/slow").bodyAsText()
            }

            // Pre-fix, CIO cancels the call context at 15s and only "first" survives.
            assertEquals("first\nsecond\n", body)
        }
        finally {
            context.close()
            slowServer.stop(0, 0)
        }
    }
}
