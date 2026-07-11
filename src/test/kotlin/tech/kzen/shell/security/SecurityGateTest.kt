package tech.kzen.shell.security

import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class SecurityGateTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun denied(
        host: String? = "localhost:8080",
        site: String? = null,
        mode: String? = null,
        method: HttpMethod = HttpMethod.Get,
        path: String = "/main/index.html"
    ): Boolean {
        return SecurityGate.deniedReasonOrNull(host, site, mode, method, path) != null
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `no fetch metadata passes`() {
        assertFalse(denied())
        assertFalse(denied(host = null))
        assertFalse(denied(path = "/shell/project/stop"))
    }


    @Test
    fun `local hosts pass with and without port`() {
        assertFalse(denied(host = "localhost"))
        assertFalse(denied(host = "127.0.0.1"))
        assertFalse(denied(host = "127.0.0.1:8080"))
        assertFalse(denied(host = "LocalHost:8080"))
    }


    @Test
    fun `non-local host denied (DNS rebinding)`() {
        assertTrue(denied(host = "evil.test:8080"))
        assertTrue(denied(host = "evil.test"))
        assertTrue(denied(host = "localhost.evil.test:8080"))
    }


    @Test
    fun `same-site fetch metadata passes`() {
        assertFalse(denied(site = "same-origin", mode = "cors"))
        assertFalse(denied(site = "same-site", mode = "no-cors"))
        assertFalse(denied(site = "none", mode = "navigate"))
    }


    @Test
    fun `cross-site subresource and fetch denied`() {
        // <img src>, script, fetch() from another page
        assertTrue(denied(site = "cross-site", mode = "no-cors"))
        assertTrue(denied(site = "cross-site", mode = "cors"))
        assertTrue(denied(site = "cross-site", mode = "no-cors", path = "/shell/project/stop"))
        // unknown future values treated as cross-site
        assertTrue(denied(site = "other", mode = "cors"))
    }


    @Test
    fun `cross-site top-level navigation passes except to mutating endpoints`() {
        assertFalse(denied(site = "cross-site", mode = "navigate"))
        assertFalse(denied(site = "cross-site", mode = "navigate", path = "/shell/project"))

        // window.location-style CSRF on the mutating endpoints
        assertTrue(denied(site = "cross-site", mode = "navigate", path = "/shell/project/start"))
        assertTrue(denied(site = "cross-site", mode = "navigate", path = "/shell/project/stop"))
    }


    @Test
    fun `non-GET cross-site navigation denied`() {
        assertTrue(denied(site = "cross-site", mode = "navigate", method = HttpMethod.Post))
    }
}
