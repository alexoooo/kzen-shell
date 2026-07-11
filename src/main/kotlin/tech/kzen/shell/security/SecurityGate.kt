package tech.kzen.shell.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory


// Trust boundary at the single ingress: the shell serves loopback-only, but the user's own
//  browser will deliver cross-site requests (CSRF) and DNS-rebound Host headers from any web
//  page it visits. Reject both before routing, uniformly protecting the shell endpoints and
//  every proxied child. Requests without fetch metadata (curl, same-machine tools) pass —
//  accepted residual on a single-user desktop. Intentionally duplicated in kzen-launcher (which
//  has no dependency on this repo, and needs the same gate for standalone dev runs) — keep the
//  copies in sync.
object SecurityGate {
    //-----------------------------------------------------------------------------------------------------------------
    private val logger = LoggerFactory.getLogger(SecurityGate::class.java)!!

    private val allowedHosts = setOf("localhost", "127.0.0.1")

    private val allowedFetchSites = setOf("same-origin", "same-site", "none")

    // Mutating endpoints are never legitimately reached by a top-level navigation, so cross-site
    //  navigations (window.location-style CSRF) are rejected for them even though navigations
    //  pass in general (external links into the UI must keep working).
    private val protectedPaths = setOf(
        "/shell/project/start",
        "/shell/project/stop")


    //-----------------------------------------------------------------------------------------------------------------
    fun install(application: Application) {
        application.intercept(ApplicationCallPipeline.Plugins) {
            val denial = deniedReasonOrNull(
                call.request.headers[HttpHeaders.Host],
                call.request.headers["Sec-Fetch-Site"],
                call.request.headers["Sec-Fetch-Mode"],
                call.request.httpMethod,
                call.request.path())

            if (denial != null) {
                logger.warn("Denied {} {} - {}", call.request.httpMethod.value, call.request.path(), denial)
                call.respond(HttpStatusCode.Forbidden)
                finish()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Null = permitted; otherwise the reason for the 403 (log-only, never sent to the client).
    fun deniedReasonOrNull(
        hostHeader: String?,
        secFetchSite: String?,
        secFetchMode: String?,
        method: HttpMethod,
        path: String
    ): String? {
        // DNS rebinding: a remote attacker's domain resolving to 127.0.0.1 arrives with their
        //  Host. Absent header (non-browser HTTP/1.0-style tools) passes.
        if (hostHeader != null) {
            val host = hostHeader.substringBefore(':').lowercase()
            if (host !in allowedHosts) {
                return "host not local: $hostHeader"
            }
        }

        val fetchSite = secFetchSite?.lowercase()
        if (fetchSite == null || fetchSite in allowedFetchSites) {
            return null
        }

        val navigation =
            method == HttpMethod.Get &&
            secFetchMode?.lowercase() == "navigate"

        if (!navigation) {
            return "cross-site request: $secFetchSite"
        }

        if (path in protectedPaths) {
            return "cross-site navigation to mutating endpoint: $secFetchSite"
        }

        return null
    }
}
