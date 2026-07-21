package tech.kzen.shell.util

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class FreePortUtilTest {
    @Test
    fun `an unbound port is free`() {
        val port = FreePortUtil.findAvailableTcpPort()

        assertTrue(FreePortUtil.isTcpPortFree(port))
    }


    @Test
    fun `a port with a live listener is not free`() {
        val port = FreePortUtil.findAvailableTcpPort()

        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use {
            assertFalse(FreePortUtil.isTcpPortFree(port))
        }
    }
}
