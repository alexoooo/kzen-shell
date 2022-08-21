package tech.kzen.shell.util

import java.net.InetAddress
import java.util.*
import javax.net.ServerSocketFactory


object FreePortUtil {
    // Dynamic ports - https://en.wikipedia.org/wiki/Registered_port
    private const val minPort = 49152
    private const val maxPort = 65535

    private val random = Random()


    fun findAvailableTcpPort(): Int {
        val portRange = maxPort - minPort
        var candidatePort: Int
        var searchCounter = 0

        do {
            check(searchCounter <= portRange) {
                String.format(
                    "Could not find an available %s port in the range [%d, %d] after %d attempts",
                    "TCP", minPort, maxPort, searchCounter
                )
            }
            candidatePort = nextRandomPort()
            searchCounter++
        }
        while (! isPortAvailable(candidatePort))

        return candidatePort
    }


    private fun isPortAvailable(port: Int): Boolean {
        @Suppress("LiftReturnOrAssignment")
        try {
            val serverSocket = ServerSocketFactory
                .getDefault()
                .createServerSocket(port, 1, InetAddress.getByName("localhost"))
            serverSocket.close()
            return true
        }
        catch (ignored: Exception) {
            return false
        }
    }


    private fun nextRandomPort(): Int {
        val portRange = maxPort - minPort
        return minPort + random.nextInt(portRange + 1)
    }
}