package tech.kzen.shell.util

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.*
import javax.net.ServerSocketFactory


object FreePortUtil {
    // Dynamic ports - https://en.wikipedia.org/wiki/Registered_port
    private const val minPort = 49152
    private const val maxPort = 65535

    // The interface the shell serves on, so the probe asks exactly the question the engine will.
    private const val loopbackHost = "127.0.0.1"

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
        while (!isPortAvailable(candidatePort))

        return candidatePort
    }


    // Pre-flight for the shell's own fixed listen port. reuseAddress is deliberately left off: on Windows
    //  SO_REUSEADDR allows binding a port that already has a live listener, which would mask the conflict.
    fun isTcpPortFree(port: Int): Boolean {
        return try {
            ServerSocket().use {
                it.bind(InetSocketAddress(loopbackHost, port))
            }
            true
        }
        catch (e: IOException) {
            false
        }
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